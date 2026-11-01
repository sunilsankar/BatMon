package com.batmon.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.longPreferencesKey
import com.batmon.app.battery.ThresholdState
import com.batmon.app.webhooks.WebhookSettings
import com.batmon.app.webhooks.toJson
import com.batmon.app.webhooks.toWebhookSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "batmon_settings")

data class MqttSettings(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 1883,
    val clientId: String = "",
    val username: String = "",
    val password: String = "",
    val useTls: Boolean = false,
    val topicPrefix: String = "",
    val discoveryPrefix: String = "homeassistant",
    val discoveryEnabled: Boolean = true,
    val retain: Boolean = true
) {
    // ponytail: client ID derived from persistent deviceId, unique per install
    fun effectiveClientId(deviceId: String): String =
        "batmon_${deviceId.removePrefix("batmon-").removePrefix("batmon_")}"

    fun effectiveTopicPrefix(deviceId: String): String =
        if (topicPrefix.isNotBlank()) topicPrefix.trimEnd('/') else "batmon/$deviceId"

    fun stateTopic(deviceId: String): String = "${effectiveTopicPrefix(deviceId)}/state"
    fun availabilityTopic(deviceId: String): String = "${effectiveTopicPrefix(deviceId)}/availability"
    fun eventTopic(deviceId: String): String = "${effectiveTopicPrefix(deviceId)}/event"
}

data class BatMonSettings(
    val deviceId: String = "batmon-device",
    val deviceName: String = "BatMon Device",
    val lowThreshold: Int = 45,
    val highThreshold: Int = 80,
    val notificationsEnabled: Boolean = true,
    val monitoringEnabled: Boolean = true,
    val realTimeMonitoringEnabled: Boolean = false,
    val thresholdState: ThresholdState = ThresholdState(),
    val lastSyncTimestampMs: Long = 0L,
    val mqtt: MqttSettings = MqttSettings(),
    val webhooks: WebhookSettings = WebhookSettings()
)

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val LOW_THRESHOLD = intPreferencesKey("low_threshold")
        val HIGH_THRESHOLD = intPreferencesKey("high_threshold")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val REAL_TIME_MONITORING = booleanPreferencesKey("real_time_monitoring")
        val THRESHOLD_LOW_FIRED = booleanPreferencesKey("threshold_low_fired")
        val THRESHOLD_HIGH_FIRED = booleanPreferencesKey("threshold_high_fired")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val MQTT_ENABLED = booleanPreferencesKey("mqtt_enabled")
        val MQTT_HOST = stringPreferencesKey("mqtt_host")
        val MQTT_PORT = intPreferencesKey("mqtt_port")
        val MQTT_CLIENT_ID = stringPreferencesKey("mqtt_client_id")
        val MQTT_USERNAME = stringPreferencesKey("mqtt_username")
        val MQTT_PASSWORD = stringPreferencesKey("mqtt_password")
        val MQTT_USE_TLS = booleanPreferencesKey("mqtt_use_tls")
        val MQTT_TOPIC_PREFIX = stringPreferencesKey("mqtt_topic_prefix")
        val MQTT_DISCOVERY_PREFIX = stringPreferencesKey("mqtt_discovery_prefix")
        val MQTT_DISCOVERY_ENABLED = booleanPreferencesKey("mqtt_discovery_enabled")
        val MQTT_RETAIN = booleanPreferencesKey("mqtt_retain")
        val WEBHOOK_CONFIG_JSON = stringPreferencesKey("webhook_config_json")
    }

    val settings: Flow<BatMonSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            BatMonSettings(
                deviceId = preferences[PreferencesKeys.DEVICE_ID] ?: "batmon-device",
                deviceName = preferences[PreferencesKeys.DEVICE_NAME] ?: "BatMon Device",
                lowThreshold = preferences[PreferencesKeys.LOW_THRESHOLD] ?: 45,
                highThreshold = preferences[PreferencesKeys.HIGH_THRESHOLD] ?: 80,
                notificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true,
                monitoringEnabled = preferences[PreferencesKeys.MONITORING_ENABLED] ?: true,
                realTimeMonitoringEnabled = preferences[PreferencesKeys.REAL_TIME_MONITORING] ?: false,
                thresholdState = ThresholdState(
                    lowFired = preferences[PreferencesKeys.THRESHOLD_LOW_FIRED] ?: false,
                    highFired = preferences[PreferencesKeys.THRESHOLD_HIGH_FIRED] ?: false
                ),
                lastSyncTimestampMs = preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] ?: 0L,
                mqtt = MqttSettings(
                    enabled = preferences[PreferencesKeys.MQTT_ENABLED] ?: false,
                    host = preferences[PreferencesKeys.MQTT_HOST] ?: "",
                    port = preferences[PreferencesKeys.MQTT_PORT] ?: 1883,
                    clientId = preferences[PreferencesKeys.MQTT_CLIENT_ID] ?: "",
                    username = preferences[PreferencesKeys.MQTT_USERNAME] ?: "",
                    password = preferences[PreferencesKeys.MQTT_PASSWORD] ?: "",
                    useTls = preferences[PreferencesKeys.MQTT_USE_TLS] ?: false,
                    topicPrefix = preferences[PreferencesKeys.MQTT_TOPIC_PREFIX] ?: "",
                    discoveryPrefix = preferences[PreferencesKeys.MQTT_DISCOVERY_PREFIX] ?: "homeassistant",
                    discoveryEnabled = preferences[PreferencesKeys.MQTT_DISCOVERY_ENABLED] ?: true,
                    retain = preferences[PreferencesKeys.MQTT_RETAIN] ?: true
                ),
                webhooks = (preferences[PreferencesKeys.WEBHOOK_CONFIG_JSON] ?: "").toWebhookSettings()
            )
        }

    suspend fun getSettings(): BatMonSettings = settings.first()

    suspend fun ensureInitialized() {
        context.dataStore.edit { preferences ->
            if (preferences[PreferencesKeys.DEVICE_ID] == null) {
                preferences[PreferencesKeys.DEVICE_ID] = "batmon-${UUID.randomUUID().toString().take(8)}"
            }
        }
    }

    suspend fun updateThresholds(low: Int, high: Int) {
        require(low in 1..98 && high in 2..99 && low < high) { "Invalid thresholds" }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOW_THRESHOLD] = low
            preferences[PreferencesKeys.HIGH_THRESHOLD] = high
        }
    }

    suspend fun updateLowThreshold(low: Int) {
        context.dataStore.edit { preferences ->
            val currentHigh = preferences[PreferencesKeys.HIGH_THRESHOLD] ?: 80
            if (low in 1..98 && low < currentHigh) {
                preferences[PreferencesKeys.LOW_THRESHOLD] = low
            }
        }
    }

    suspend fun updateHighThreshold(high: Int) {
        context.dataStore.edit { preferences ->
            val currentLow = preferences[PreferencesKeys.LOW_THRESHOLD] ?: 45
            if (high in 2..99 && currentLow < high) {
                preferences[PreferencesKeys.HIGH_THRESHOLD] = high
            }
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MONITORING_ENABLED] = enabled
        }
    }

    suspend fun setRealTimeMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REAL_TIME_MONITORING] = enabled
        }
    }

    suspend fun updateThresholdState(state: ThresholdState) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THRESHOLD_LOW_FIRED] = state.lowFired
            preferences[PreferencesKeys.THRESHOLD_HIGH_FIRED] = state.highFired
        }
    }

    suspend fun recordSyncTimestamp(timestampMs: Long = System.currentTimeMillis()) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = timestampMs
        }
    }

    suspend fun setDeviceInfo(deviceId: String, deviceName: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVICE_ID] = deviceId
            preferences[PreferencesKeys.DEVICE_NAME] = deviceName
        }
    }

    suspend fun setMqttEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MQTT_ENABLED] = enabled
        }
    }

    suspend fun updateMqttSettings(mqtt: MqttSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MQTT_ENABLED] = mqtt.enabled
            preferences[PreferencesKeys.MQTT_HOST] = mqtt.host
            preferences[PreferencesKeys.MQTT_PORT] = mqtt.port
            preferences[PreferencesKeys.MQTT_CLIENT_ID] = mqtt.clientId
            preferences[PreferencesKeys.MQTT_USERNAME] = mqtt.username
            preferences[PreferencesKeys.MQTT_PASSWORD] = mqtt.password
            preferences[PreferencesKeys.MQTT_USE_TLS] = mqtt.useTls
            preferences[PreferencesKeys.MQTT_TOPIC_PREFIX] = mqtt.topicPrefix
            preferences[PreferencesKeys.MQTT_DISCOVERY_PREFIX] = mqtt.discoveryPrefix
            preferences[PreferencesKeys.MQTT_DISCOVERY_ENABLED] = mqtt.discoveryEnabled
            preferences[PreferencesKeys.MQTT_RETAIN] = mqtt.retain
        }
    }

    suspend fun updateWebhookSettings(webhooks: WebhookSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WEBHOOK_CONFIG_JSON] = webhooks.toJson()
        }
    }

    suspend fun setWebhooksEnabled(enabled: Boolean) {
        val current = getSettings().webhooks
        updateWebhookSettings(current.copy(enabled = enabled))
    }
}
