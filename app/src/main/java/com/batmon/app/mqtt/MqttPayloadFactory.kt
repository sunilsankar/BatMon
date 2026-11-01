package com.batmon.app.mqtt

import com.batmon.app.battery.BatteryState
import com.batmon.app.data.BatMonSettings
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class DiscoveryEntity(
    val configTopic: String,
    val payloadJson: String
)

object MqttPayloadFactory {

    fun buildStatePayload(
        batteryState: BatteryState,
        settings: BatMonSettings,
        timestampMs: Long = System.currentTimeMillis()
    ): String {
        val json = JSONObject()
        json.put("device_id", settings.deviceId)
        json.put("device_name", settings.deviceName)
        json.put("battery_percent", batteryState.percentage)
        json.put("charging_status", batteryState.chargingStatus.lowercase())
        json.put("is_charging", batteryState.isCharging)
        json.put("low_threshold", settings.lowThreshold)
        json.put("high_threshold", settings.highThreshold)
        json.put("monitoring_enabled", settings.monitoringEnabled)
        json.put("timestamp", Instant.ofEpochMilli(timestampMs).toString())

        batteryState.health?.let { json.put("health", it) }
        batteryState.temperatureCelsius?.let { json.put("temperature_c", it) }
        batteryState.pluggedSource?.let { json.put("plugged_source", it) }

        return json.toString()
    }

    fun buildEventPayload(
        event: String,
        batteryState: BatteryState,
        settings: BatMonSettings,
        thresholdPercent: Int? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): String {
        val json = JSONObject()
        json.put("event", event)
        json.put("device_id", settings.deviceId)
        json.put("battery_percent", batteryState.percentage)
        if (thresholdPercent != null) {
            json.put("threshold_percent", thresholdPercent)
        }
        json.put("is_charging", batteryState.isCharging)
        json.put("charging_status", batteryState.chargingStatus.lowercase())
        json.put("timestamp", Instant.ofEpochMilli(timestampMs).toString())
        return json.toString()
    }

    fun buildDiscoveryEntities(
        settings: BatMonSettings
    ): List<DiscoveryEntity> {
        val deviceId = settings.deviceId
        val sanitizedId = deviceId.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
        val discoveryPrefix = settings.mqtt.discoveryPrefix.trimEnd('/')
        val stateTopic = settings.mqtt.stateTopic(deviceId)
        val availabilityTopic = settings.mqtt.availabilityTopic(deviceId)

        val deviceObj = JSONObject().apply {
            put("identifiers", JSONArray().put("batmon_$sanitizedId"))
            put("name", settings.deviceName)
            put("model", "Android Battery Monitor")
            put("manufacturer", "BatMon")
            put("sw_version", "1.0.0")
        }

        fun baseEntity(name: String, uniqueId: String): JSONObject {
            return JSONObject().apply {
                put("device", deviceObj)
                put("availability_topic", availabilityTopic)
                put("payload_available", "online")
                put("payload_not_available", "offline")
                put("state_topic", stateTopic)
                put("name", name)
                put("unique_id", uniqueId)
            }
        }

        val entities = mutableListOf<DiscoveryEntity>()

        // 1. Battery Percentage Sensor
        val batteryObj = baseEntity("Battery", "batmon_${sanitizedId}_battery").apply {
            put("device_class", "battery")
            put("state_class", "measurement")
            put("unit_of_measurement", "%")
            put("value_template", "{{ value_json.battery_percent }}")
        }
        entities.add(
            DiscoveryEntity(
                configTopic = "$discoveryPrefix/sensor/batmon_${sanitizedId}/battery/config",
                payloadJson = batteryObj.toString()
            )
        )

        // 2. Charging Binary Sensor
        val chargingObj = baseEntity("Charging", "batmon_${sanitizedId}_charging").apply {
            put("device_class", "battery_charging")
            put("payload_on", "true")
            put("payload_off", "false")
            put("value_template", "{{ value_json.is_charging }}")
        }
        entities.add(
            DiscoveryEntity(
                configTopic = "$discoveryPrefix/binary_sensor/batmon_${sanitizedId}/charging/config",
                payloadJson = chargingObj.toString()
            )
        )

        // 3. Monitoring Diagnostic Binary Sensor
        val monitoringObj = baseEntity("Monitoring", "batmon_${sanitizedId}_monitoring").apply {
            put("entity_category", "diagnostic")
            put("payload_on", "true")
            put("payload_off", "false")
            put("value_template", "{{ value_json.monitoring_enabled }}")
        }
        entities.add(
            DiscoveryEntity(
                configTopic = "$discoveryPrefix/binary_sensor/batmon_${sanitizedId}/monitoring/config",
                payloadJson = monitoringObj.toString()
            )
        )

        // 4. Battery Health Sensor
        val healthObj = baseEntity("Battery Health", "batmon_${sanitizedId}_health").apply {
            put("entity_category", "diagnostic")
            put("value_template", "{{ value_json.health | default('unknown') }}")
            put("icon", "mdi:battery-heart-variant")
        }
        entities.add(
            DiscoveryEntity(
                configTopic = "$discoveryPrefix/sensor/batmon_${sanitizedId}/health/config",
                payloadJson = healthObj.toString()
            )
        )

        // 5. Last Update Sensor
        val lastUpdateObj = baseEntity("Last Update", "batmon_${sanitizedId}_last_update").apply {
            put("device_class", "timestamp")
            put("entity_category", "diagnostic")
            put("value_template", "{{ value_json.timestamp }}")
        }
        entities.add(
            DiscoveryEntity(
                configTopic = "$discoveryPrefix/sensor/batmon_${sanitizedId}/last_update/config",
                payloadJson = lastUpdateObj.toString()
            )
        )

        return entities
    }
}
