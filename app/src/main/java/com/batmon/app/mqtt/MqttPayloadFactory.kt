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

    fun buildMetricReadings(
        batteryState: BatteryState,
        timestampMs: Long = System.currentTimeMillis()
    ): Map<String, String> {
        val readings = mutableMapOf<String, String>()
        readings["battery_level"] = batteryState.percentage.toString()
        readings["charging_status"] = batteryState.chargingStatus.lowercase()
        readings["is_charging"] = if (batteryState.isCharging) "ON" else "OFF"
        readings["last_update"] = Instant.ofEpochMilli(timestampMs).toString()
        batteryState.temperatureCelsius?.let {
            readings["temperature"] = it.toDouble().toString()
        }
        batteryState.health?.let {
            readings["health"] = it
        }
        return readings
    }

    fun buildDiscoveryEntities(
        settings: BatMonSettings,
        deviceModel: String = android.os.Build.MODEL?.takeIf { it.isNotBlank() } ?: settings.deviceName
    ): List<DiscoveryEntity> {
        val prefix = settings.mqtt.effectiveTopicPrefix(settings.deviceId)
        val shortId = settings.mqtt.deviceShortId(settings.deviceId)
        val statusTopic = settings.mqtt.statusTopic(settings.deviceId)

        val devObj = JSONObject().apply {
            put("ids", JSONArray().put("${prefix}_$shortId"))
            put("name", "Batmon $deviceModel")
            put("mdl", deviceModel)
            put("mf", "Android Batmon")
        }

        fun sensorEntity(
            metricTag: String,
            name: String,
            deviceClass: String? = null,
            stateClass: String? = null,
            unitOfMeasurement: String? = null,
            entityCategory: String? = null,
            icon: String? = null
        ): DiscoveryEntity {
            val json = JSONObject().apply {
                put("name", name)
                put("uniq_id", "${prefix}_${shortId}_$metricTag")
                put("stat_t", "${prefix}_${shortId}/$metricTag")
                put("avty_t", statusTopic)
                if (deviceClass != null) put("dev_cla", deviceClass)
                if (stateClass != null) put("stat_cla", stateClass)
                if (unitOfMeasurement != null) put("unit_of_meas", unitOfMeasurement)
                if (entityCategory != null) put("ent_cat", entityCategory)
                if (icon != null) put("ic", icon)
                put("dev", devObj)
            }
            return DiscoveryEntity(
                configTopic = "homeassistant/sensor/${prefix}_${shortId}/$metricTag/config",
                payloadJson = json.toString()
            )
        }

        val entities = mutableListOf<DiscoveryEntity>()

        // 1. battery_level
        entities.add(
            sensorEntity(
                metricTag = "battery_level",
                name = "Battery Level",
                deviceClass = "battery",
                stateClass = "measurement",
                unitOfMeasurement = "%"
            )
        )

        // 2. charging_status
        entities.add(
            sensorEntity(
                metricTag = "charging_status",
                name = "Charging Status"
            )
        )

        // 3. temperature
        entities.add(
            sensorEntity(
                metricTag = "temperature",
                name = "Temperature",
                deviceClass = "temperature",
                stateClass = "measurement",
                unitOfMeasurement = "°C"
            )
        )

        // 4. health
        entities.add(
            sensorEntity(
                metricTag = "health",
                name = "Battery Health",
                entityCategory = "diagnostic",
                icon = "mdi:battery-heart-variant"
            )
        )

        // 5. last_update
        entities.add(
            sensorEntity(
                metricTag = "last_update",
                name = "Last Update",
                deviceClass = "timestamp",
                entityCategory = "diagnostic"
            )
        )

        // 6. is_charging (binary_sensor)
        val binaryJson = JSONObject().apply {
            put("name", "Charging")
            put("uniq_id", "${prefix}_${shortId}_is_charging")
            put("stat_t", "${prefix}_${shortId}/is_charging")
            put("avty_t", statusTopic)
            put("dev_cla", "battery_charging")
            put("pl_on", "ON")
            put("pl_off", "OFF")
            put("dev", devObj)
        }
        entities.add(
            DiscoveryEntity(
                configTopic = "homeassistant/binary_sensor/${prefix}_${shortId}/is_charging/config",
                payloadJson = binaryJson.toString()
            )
        )

        return entities
    }
}
