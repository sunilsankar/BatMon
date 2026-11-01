package com.batmon.app.mqtt

import android.util.Log
import com.batmon.app.battery.BatteryState
import com.batmon.app.data.BatMonSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface MqttConnectionStatus {
    data object Disconnected : MqttConnectionStatus
    data object Connecting : MqttConnectionStatus
    data object Connected : MqttConnectionStatus
    data class Error(val message: String) : MqttConnectionStatus
}

private class PahoMqttClient(
    serverUri: String,
    clientId: String,
    settings: BatMonSettings
) {
    private val client = org.eclipse.paho.mqttv5.client.MqttClient(
        serverUri,
        clientId,
        org.eclipse.paho.mqttv5.client.persist.MemoryPersistence()
    )

    private val options = org.eclipse.paho.mqttv5.client.MqttConnectionOptions().apply {
        isCleanStart = true
        connectionTimeout = 10
        keepAliveInterval = 60
        isAutomaticReconnect = true
        if (settings.mqtt.username.isNotBlank()) {
            userName = settings.mqtt.username.trim()
        }
        if (settings.mqtt.password.isNotBlank()) {
            password = settings.mqtt.password.trim().toByteArray(Charsets.UTF_8)
        }
        val availabilityTopic = settings.mqtt.availabilityTopic(settings.deviceId)
        val willMsg = org.eclipse.paho.mqttv5.common.MqttMessage("offline".toByteArray(Charsets.UTF_8)).apply {
            qos = 1
            isRetained = true
        }
        setWill(availabilityTopic, willMsg)
    }

    fun connect() = client.connect(options)

    fun publish(topic: String, payload: ByteArray, qos: Int, retained: Boolean) {
        val msg = org.eclipse.paho.mqttv5.common.MqttMessage(payload).apply {
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

    private fun sanitizeError(e: Throwable, password: String): String {
        val reasonCode = when (e) {
            is org.eclipse.paho.mqttv5.common.MqttException -> e.reasonCode
            else -> null
        }

        if (reasonCode in listOf(4, 5, 134, 135)) {
            val codeStr = if (reasonCode != null) " (code $reasonCode)" else ""
            return "Not authorized to connect$codeStr: verify broker username, password, and ACL permissions for MQTT 5.0"
        }

        var result = e.message ?: "Unknown MQTT error"
        if (password.isNotBlank()) {
            result = result.replace(password, "******")
        }
        return result
    }

    private fun resolveServerUri(settings: BatMonSettings): String {
        val scheme = if (settings.mqtt.useTls) "ssl" else "tcp"
        val host = settings.mqtt.host.trim()
        val port = settings.mqtt.port
        return "$scheme://$host:$port"
    }

    private fun computeConfigKey(settings: BatMonSettings, serverUri: String, clientId: String): String {
        return "$serverUri|$clientId|${settings.mqtt.username}|${settings.mqtt.password}|${settings.mqtt.useTls}"
    }

    private fun createClient(settings: BatMonSettings, serverUri: String, clientId: String) =
        PahoMqttClient(serverUri, clientId, settings)

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

                // Publish state
                val statePayload = MqttPayloadFactory.buildStatePayload(batteryState, settings, timestampMs)
                val stateTopic = settings.mqtt.stateTopic(settings.deviceId)
                activeClient.publish(
                    topic = stateTopic,
                    payload = statePayload.toByteArray(Charsets.UTF_8),
                    qos = 1,
                    retained = settings.mqtt.retain
                )

                _status.value = MqttConnectionStatus.Connected
                Result.success(Unit)
            } catch (e: Exception) {
                val safeMsg = sanitizeError(e, settings.mqtt.password)
                Log.w("MqttPublisher", "Failed to publish state: $safeMsg")
                _status.value = MqttConnectionStatus.Error(safeMsg)
                Result.failure(e)
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
                val safeMsg = sanitizeError(e, settings.mqtt.password)
                Log.w("MqttPublisher", "Failed to publish event: $safeMsg")
                _status.value = MqttConnectionStatus.Error(safeMsg)
                Result.failure(e)
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
                val safeMsg = sanitizeError(e, settings.mqtt.password)
                Log.w("MqttPublisher", "Failed to publish discovery: $safeMsg")
                Result.failure(e)
            }
        }
    }

    private fun ensureConnected(settings: BatMonSettings) {
        val serverUri = resolveServerUri(settings)
        val clientId = settings.mqtt.effectiveClientId(settings.deviceId)
        val currentConfigKey = computeConfigKey(settings, serverUri, clientId)

        if (client != null && client?.isConnected == true && activeConfigKey == currentConfigKey) {
            return
        }

        disconnectInternal()

        _status.value = MqttConnectionStatus.Connecting
        val newClient = createClient(settings, serverUri, clientId)
        newClient.connect()

        // Publish availability online immediately
        val availabilityTopic = settings.mqtt.availabilityTopic(settings.deviceId)
        newClient.publish(
            topic = availabilityTopic,
            payload = "online".toByteArray(Charsets.UTF_8),
            qos = 1,
            retained = true
        )

        client = newClient
        activeConfigKey = currentConfigKey
        _status.value = MqttConnectionStatus.Connected

        // Publish discovery on initial connection
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
        if (settings.mqtt.host.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Broker host cannot be blank"))
        }

        val serverUri = resolveServerUri(settings)
        val clientId = settings.mqtt.effectiveClientId(settings.deviceId)
        val testClient = createClient(settings, serverUri, clientId)
        try {
            testClient.connect()
            if (!testClient.isConnected) {
                return@withContext Result.failure(IllegalStateException("Could not establish connection to $serverUri"))
            }

            // Publish availability (online)
            val availabilityTopic = settings.mqtt.availabilityTopic(settings.deviceId)
            testClient.publish(
                topic = availabilityTopic,
                payload = "online".toByteArray(Charsets.UTF_8),
                qos = 1,
                retained = true
            )

            // Publish battery state
            val stateTopic = settings.mqtt.stateTopic(settings.deviceId)
            val stateToPublish = batteryState ?: BatteryState(
                percentage = 100,
                chargingStatus = "Discharging",
                isCharging = false,
                timestampMs = System.currentTimeMillis()
            )
            val statePayload = MqttPayloadFactory.buildStatePayload(stateToPublish, settings)
            testClient.publish(
                topic = stateTopic,
                payload = statePayload.toByteArray(Charsets.UTF_8),
                qos = 1,
                retained = settings.mqtt.retain
            )

            // Best-effort Home Assistant auto-discovery
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

            Result.success("Connected & published test state to $stateTopic (MQTT 5.0)!")
        } catch (e: Exception) {
            try { testClient.disconnect() } catch (_: Exception) {}
            val safeMessage = sanitizeError(e, settings.mqtt.password)
            Result.failure(Exception(safeMessage))
        }
    }
}
