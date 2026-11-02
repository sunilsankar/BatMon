package com.batmon.app.mqtt

import android.util.Log
import com.batmon.app.battery.BatteryState
import com.batmon.app.data.BatMonSettings
import com.batmon.app.util.safeMessage
import com.batmon.app.util.trimBlanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

sealed interface MqttConnectionStatus {
    data object Disconnected : MqttConnectionStatus
    data object Connecting : MqttConnectionStatus
    data object Connected : MqttConnectionStatus
    data class Error(val message: String) : MqttConnectionStatus
}

private class PahoMqttClient(
    serverUri: String,
    clientId: String,
    settings: BatMonSettings,
    anonymous: Boolean = false
) {
    private val client = MqttClient(
        serverUri,
        clientId,
        MemoryPersistence()
    )

    private val options = MqttConnectOptions().apply {
        mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
        isCleanSession = true
        connectionTimeout = 10
        keepAliveInterval = 60
        isAutomaticReconnect = true

        val username = settings.mqtt.username.trimBlanks()
        val password = settings.mqtt.password.trimBlanks()
        if (!anonymous && username.isNotEmpty()) {
            userName = username
            this.password = password.toCharArray()
        }

        val statusTopic = settings.mqtt.statusTopic(settings.deviceId)
        setWill(statusTopic, "offline".toByteArray(Charsets.UTF_8), 1, true)
    }

    fun connect() = client.connect(options)

    fun publish(topic: String, payload: ByteArray, qos: Int, retained: Boolean) {
        val msg = MqttMessage(payload).apply {
            this.qos = qos
            isRetained = retained
        }
        client.publish(topic, msg)
    }

    fun disconnect() {
        try {
            if (client.isConnected) client.disconnectForcibly(1000)
            client.close()
        } catch (_: Exception) {}
    }

    val isConnected: Boolean get() = client.isConnected
}

class MqttPublisher {

    private val mutex = Mutex()
    private var client: PahoMqttClient? = null
    private var activeConfigKey: String? = null

    private val _status = MutableStateFlow<MqttConnectionStatus>(MqttConnectionStatus.Disconnected)
    val status: StateFlow<MqttConnectionStatus> = _status.asStateFlow()

    private fun checkBrokerSocket(host: String, port: Int, timeoutMs: Int = 3000) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
        }
    }

    internal fun sanitizeError(
        e: Throwable,
        password: String,
        username: String = "",
        clientId: String = "",
        anonymousProbeResult: Boolean? = null
    ): String {
        if (e is MqttException) {
            val code = e.reasonCode
            if (code == MqttException.REASON_CODE_FAILED_AUTHENTICATION.toInt() ||
                code == MqttException.REASON_CODE_NOT_AUTHORIZED.toInt()
            ) {
                return buildAuthErrorMessage(code, username, password, clientId, anonymousProbeResult)
            }
        }

        var result = e.safeMessage()
        val rawPassword = password.trimBlanks()
        if (rawPassword.isNotBlank()) {
            result = result.replace(rawPassword, "******")
        }
        return result
    }

    internal fun buildAuthErrorMessage(
        reasonCode: Int,
        username: String = "",
        password: String = "",
        clientId: String = "",
        anonymousProbeResult: Boolean? = null
    ): String {
        val rcText = if (reasonCode == MqttException.REASON_CODE_FAILED_AUTHENTICATION.toInt()) {
            "Broker rejected username/password (rc 4)"
        } else {
            "Broker refused connection: not authorised (rc 5)"
        }

        val trimmedUser = username.trimBlanks()
        val trimmedPass = password.trimBlanks()
        val details = buildString {
            if (trimmedUser.isNotEmpty()) {
                append("user: \"$trimmedUser\", password: ${trimmedPass.length} chars")
                if (password.length != trimmedPass.length) append(" (blanks trimmed)")
            } else {
                append("anonymous")
            }
            if (clientId.isNotEmpty()) {
                append(", client ID: $clientId")
            }
        }

        val probeText = when (anonymousProbeResult) {
            true -> " — Probe: broker permits anonymous (user missing from password file or password differs)"
            false -> " — Probe: anonymous also refused"
            null -> ""
        }

        return "$rcText [$details]$probeText"
    }

    private fun resolveServerUri(settings: BatMonSettings): String {
        val host = settings.mqtt.host.trim()
        val port = settings.mqtt.port
        return "tcp://$host:$port"
    }

    private fun computeConfigKey(settings: BatMonSettings, serverUri: String, clientId: String): String {
        return "$serverUri|$clientId|${settings.mqtt.username.trimBlanks()}|${settings.mqtt.password.trimBlanks()}"
    }

    private fun createClient(
        settings: BatMonSettings,
        serverUri: String,
        clientId: String,
        anonymous: Boolean = false
    ) = PahoMqttClient(serverUri, clientId, settings, anonymous)

    suspend fun publishState(
        batteryState: BatteryState,
        settings: BatMonSettings,
        timestampMs: Long = System.currentTimeMillis()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!settings.mqtt.enabled || settings.mqtt.host.isBlank()) {
            return@withContext Result.success(Unit)
        }

        mutex.withLock {
            try {
                ensureConnected(settings)

                val activeClient = client ?: return@withContext Result.failure(
                    IllegalStateException("MQTT client is null")
                )

                // 1. Re-assert online availability status retained so entities never stay stuck in offline/unavailable
                val statusTopic = settings.mqtt.statusTopic(settings.deviceId)
                activeClient.publish(
                    topic = statusTopic,
                    payload = "online".toByteArray(Charsets.UTF_8),
                    qos = 1,
                    retained = true
                )

                // 2. Publish scalar telemetry readings to individual metric topics (retained)
                val readings = MqttPayloadFactory.buildMetricReadings(batteryState, timestampMs)
                for ((tag, value) in readings) {
                    val topic = settings.mqtt.metricTopic(settings.deviceId, tag)
                    activeClient.publish(
                        topic = topic,
                        payload = value.toByteArray(Charsets.UTF_8),
                        qos = 1,
                        retained = true
                    )
                }

                // 3. Publish full JSON state topic (retained)
                val statePayload = MqttPayloadFactory.buildStatePayload(batteryState, settings, timestampMs)
                val stateTopic = settings.mqtt.stateTopic(settings.deviceId)
                activeClient.publish(
                    topic = stateTopic,
                    payload = statePayload.toByteArray(Charsets.UTF_8),
                    qos = 1,
                    retained = true
                )

                _status.value = MqttConnectionStatus.Connected
                Result.success(Unit)
            } catch (e: Exception) {
                val safeMsg = sanitizeError(
                    e = e,
                    password = settings.mqtt.password,
                    username = settings.mqtt.username,
                    clientId = settings.mqtt.effectiveClientId(settings.deviceId)
                )
                Log.w("MqttPublisher", "Failed to publish state: $safeMsg")
                _status.value = MqttConnectionStatus.Error(safeMsg)
                Result.failure(Exception(safeMsg))
            }
        }
    }

    suspend fun publishEvent(
        event: String,
        batteryState: BatteryState,
        settings: BatMonSettings,
        thresholdPercent: Int? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!settings.mqtt.enabled || settings.mqtt.host.isBlank()) {
            return@withContext Result.success(Unit)
        }

        mutex.withLock {
            try {
                ensureConnected(settings)

                val activeClient = client ?: return@withContext Result.failure(
                    IllegalStateException("MQTT client is null")
                )

                val eventPayload = MqttPayloadFactory.buildEventPayload(
                    event = event,
                    batteryState = batteryState,
                    settings = settings,
                    thresholdPercent = thresholdPercent,
                    timestampMs = timestampMs
                )
                val eventTopic = settings.mqtt.eventTopic(settings.deviceId)
                activeClient.publish(
                    topic = eventTopic,
                    payload = eventPayload.toByteArray(Charsets.UTF_8),
                    qos = 1,
                    retained = false
                )

                _status.value = MqttConnectionStatus.Connected
                Result.success(Unit)
            } catch (e: Exception) {
                val safeMsg = sanitizeError(
                    e = e,
                    password = settings.mqtt.password,
                    username = settings.mqtt.username,
                    clientId = settings.mqtt.effectiveClientId(settings.deviceId)
                )
                Log.w("MqttPublisher", "Failed to publish event: $safeMsg")
                _status.value = MqttConnectionStatus.Error(safeMsg)
                Result.failure(Exception(safeMsg))
            }
        }
    }

    suspend fun publishDiscovery(settings: BatMonSettings): Result<Unit> = withContext(Dispatchers.IO) {
        if (!settings.mqtt.enabled || !settings.mqtt.discoveryEnabled || settings.mqtt.host.isBlank()) {
            return@withContext Result.success(Unit)
        }

        mutex.withLock {
            try {
                ensureConnected(settings)

                val activeClient = client ?: return@withContext Result.failure(
                    IllegalStateException("MQTT client is null")
                )

                val entities = MqttPayloadFactory.buildDiscoveryEntities(settings)
                for (entity in entities) {
                    activeClient.publish(
                        topic = entity.configTopic,
                        payload = entity.payloadJson.toByteArray(Charsets.UTF_8),
                        qos = 1,
                        retained = true
                    )
                }

                Result.success(Unit)
            } catch (e: Exception) {
                val safeMsg = sanitizeError(
                    e = e,
                    password = settings.mqtt.password,
                    username = settings.mqtt.username,
                    clientId = settings.mqtt.effectiveClientId(settings.deviceId)
                )
                Log.w("MqttPublisher", "Failed to publish discovery: $safeMsg")
                Result.failure(Exception(safeMsg))
            }
        }
    }

    private fun ensureConnected(settings: BatMonSettings) {
        val host = settings.mqtt.host.trim()
        val port = settings.mqtt.port
        val serverUri = resolveServerUri(settings)
        val clientId = settings.mqtt.effectiveClientId(settings.deviceId)
        val currentConfigKey = computeConfigKey(settings, serverUri, clientId)

        if (client != null && client?.isConnected == true && activeConfigKey == currentConfigKey) {
            return
        }

        disconnectInternal()

        // 1. Pre-flight TCP socket reachability check
        try {
            checkBrokerSocket(host, port, 3000)
            Log.i("MqttPublisher", "Internet connectivity to MQTT broker $host at port $port available")
        } catch (e: Exception) {
            val msg = "Cannot reach $host:$port (check IP/port/Wi-Fi)"
            Log.w("MqttPublisher", msg)
            throw IOException(msg, e)
        }

        _status.value = MqttConnectionStatus.Connecting
        val newClient = createClient(settings, serverUri, clientId)
        newClient.connect()

        // 2. Publish online availability status retained
        val statusTopic = settings.mqtt.statusTopic(settings.deviceId)
        newClient.publish(
            topic = statusTopic,
            payload = "online".toByteArray(Charsets.UTF_8),
            qos = 1,
            retained = true
        )

        client = newClient
        activeConfigKey = currentConfigKey
        _status.value = MqttConnectionStatus.Connected

        // 3. Publish Home Assistant auto-discovery if enabled
        if (settings.mqtt.discoveryEnabled) {
            val entities = MqttPayloadFactory.buildDiscoveryEntities(settings)
            for (entity in entities) {
                newClient.publish(
                    topic = entity.configTopic,
                    payload = entity.payloadJson.toByteArray(Charsets.UTF_8),
                    qos = 1,
                    retained = true
                )
            }
        }
    }

    suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            disconnectInternal()
        }
    }

    private fun disconnectInternal() {
        try {
            client?.let {
                if (it.isConnected) {
                    it.disconnect()
                }
            }
        } catch (e: Exception) {
            Log.d("MqttPublisher", "Disconnect cleanup: ${e.message}")
        } finally {
            client = null
            activeConfigKey = null
            _status.value = MqttConnectionStatus.Disconnected
        }
    }

    suspend fun testConnection(
        settings: BatMonSettings,
        batteryState: BatteryState? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val host = settings.mqtt.host.trim()
        val port = settings.mqtt.port
        if (host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Broker host cannot be blank"))
        }

        // 1. Pre-flight TCP reachability check
        try {
            checkBrokerSocket(host, port, 3000)
            Log.i("MqttPublisher", "Internet connectivity to MQTT broker $host at port $port available")
        } catch (e: Exception) {
            val msg = "Cannot reach $host:$port (check IP/port/Wi-Fi)"
            return@withContext Result.failure(IOException(msg, e))
        }

        val serverUri = resolveServerUri(settings)
        val clientId = settings.mqtt.effectiveClientId(settings.deviceId)
        val testClient = createClient(settings, serverUri, clientId)
        try {
            testClient.connect()
            if (!testClient.isConnected) {
                return@withContext Result.failure(IllegalStateException("Could not establish connection to $serverUri"))
            }

            // 2. Publish online to status topic
            val statusTopic = settings.mqtt.statusTopic(settings.deviceId)
            testClient.publish(
                topic = statusTopic,
                payload = "online".toByteArray(Charsets.UTF_8),
                qos = 1,
                retained = true
            )

            // 3. Publish telemetry if battery state is available (retained)
            val stateToPublish = batteryState?.let {
                if (it.temperatureCelsius == null) it.copy(temperatureCelsius = 25.0f) else it
            } ?: BatteryState(
                percentage = 100,
                chargingStatus = "Discharging",
                isCharging = false,
                temperatureCelsius = 25.0f,
                health = "Good",
                timestampMs = System.currentTimeMillis()
            )
            val readings = MqttPayloadFactory.buildMetricReadings(stateToPublish)
            for ((tag, value) in readings) {
                val topic = settings.mqtt.metricTopic(settings.deviceId, tag)
                testClient.publish(
                    topic = topic,
                    payload = value.toByteArray(Charsets.UTF_8),
                    qos = 1,
                    retained = true
                )
            }

            // Also publish state JSON retained during test
            val statePayload = MqttPayloadFactory.buildStatePayload(stateToPublish, settings)
            val stateTopic = settings.mqtt.stateTopic(settings.deviceId)
            testClient.publish(
                topic = stateTopic,
                payload = statePayload.toByteArray(Charsets.UTF_8),
                qos = 1,
                retained = true
            )

            // 4. Trigger Home Assistant Auto Discovery registration if enabled
            if (settings.mqtt.discoveryEnabled) {
                try {
                    val entities = MqttPayloadFactory.buildDiscoveryEntities(settings)
                    for (entity in entities) {
                        testClient.publish(
                            topic = entity.configTopic,
                            payload = entity.payloadJson.toByteArray(Charsets.UTF_8),
                            qos = 1,
                            retained = true
                        )
                    }
                } catch (discoveryErr: Exception) {
                    Log.w("MqttPublisher", "Discovery publish during test notice: ${discoveryErr.message}")
                }
            }

            testClient.disconnect()

            val username = settings.mqtt.username.trimBlanks()
            val authInfo = if (username.isNotEmpty()) {
                "Authenticated as $username"
            } else {
                if (settings.mqtt.password.trimBlanks().isNotEmpty()) {
                    "Anonymous (warning: password configured but username is blank)"
                } else {
                    "Anonymous"
                }
            }
            Result.success("Connected to $host:$port ($authInfo)")
        } catch (e: Exception) {
            try { testClient.disconnect() } catch (_: Exception) {}

            var anonymousProbeResult: Boolean? = null
            if (e is MqttException &&
                (e.reasonCode == MqttException.REASON_CODE_FAILED_AUTHENTICATION.toInt() ||
                 e.reasonCode == MqttException.REASON_CODE_NOT_AUTHORIZED.toInt()) &&
                settings.mqtt.username.trimBlanks().isNotEmpty()
            ) {
                try {
                    val anonClient = createClient(settings, serverUri, "batmon-anon-probe", anonymous = true)
                    anonClient.connect()
                    anonymousProbeResult = anonClient.isConnected
                    try { anonClient.disconnect() } catch (_: Exception) {}
                } catch (_: Exception) {
                    anonymousProbeResult = false
                }
            }

            val safeMessage = sanitizeError(
                e = e,
                password = settings.mqtt.password,
                username = settings.mqtt.username,
                clientId = clientId,
                anonymousProbeResult = anonymousProbeResult
            )
            Result.failure(Exception(safeMessage))
        }
    }
}
