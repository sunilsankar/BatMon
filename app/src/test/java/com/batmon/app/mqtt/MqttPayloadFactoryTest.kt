package com.batmon.app.mqtt

import com.batmon.app.battery.BatteryState
import com.batmon.app.data.BatMonSettings
import com.batmon.app.data.MqttSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MqttPayloadFactoryTest {

    @Test
    fun buildStatePayload_containsAllRequiredFields() {
        val battery = BatteryState(
            percentage = 72,
            isCharging = false,
            chargingStatus = "Discharging",
            pluggedSource = "Unplugged",
            temperatureCelsius = 26.5f,
            health = "Good",
            timestampMs = 1700000000000L
        )
        val settings = BatMonSettings(
            deviceId = "tablet-living-room",
            deviceName = "Living Room Tablet",
            lowThreshold = 40,
            highThreshold = 85,
            monitoringEnabled = true
        )

        val jsonString = MqttPayloadFactory.buildStatePayload(
            batteryState = battery,
            settings = settings,
            timestampMs = 1700000000000L
        )

        val json = JSONObject(jsonString)
        assertEquals("tablet-living-room", json.getString("device_id"))
        assertEquals("Living Room Tablet", json.getString("device_name"))
        assertEquals(72, json.getInt("battery_percent"))
        assertEquals("discharging", json.getString("charging_status"))
        assertEquals(false, json.getBoolean("is_charging"))
        assertEquals(40, json.getInt("low_threshold"))
        assertEquals(85, json.getInt("high_threshold"))
        assertEquals(true, json.getBoolean("monitoring_enabled"))
        assertEquals("Good", json.getString("health"))
        assertEquals(26.5, json.getDouble("temperature_c"), 0.01)
        assertEquals("Unplugged", json.getString("plugged_source"))
        assertEquals(Instant.ofEpochMilli(1700000000000L).toString(), json.getString("timestamp"))
    }

    @Test
    fun buildStatePayload_omitsMissingOptionalFields() {
        val battery = BatteryState(
            percentage = 50,
            isCharging = true,
            chargingStatus = "Charging",
            pluggedSource = null,
            temperatureCelsius = null,
            health = null,
            timestampMs = 1700000000000L
        )
        val settings = BatMonSettings(deviceId = "test-phone")

        val jsonString = MqttPayloadFactory.buildStatePayload(
            batteryState = battery,
            settings = settings,
            timestampMs = 1700000000000L
        )

        val json = JSONObject(jsonString)
        assertEquals(50, json.getInt("battery_percent"))
        assertEquals("charging", json.getString("charging_status"))
        assertTrue(json.getBoolean("is_charging"))
        assertFalse(json.has("health"))
        assertFalse(json.has("temperature_c"))
        assertFalse(json.has("plugged_source"))
    }

    @Test
    fun buildDiscoveryEntities_createsFiveStableHaEntities() {
        val settings = BatMonSettings(
            deviceId = "office-pixel-7",
            deviceName = "Office Pixel",
            mqtt = MqttSettings(
                discoveryPrefix = "homeassistant",
                topicPrefix = "batmon/office-pixel-7"
            )
        )

        val entities = MqttPayloadFactory.buildDiscoveryEntities(settings)
        assertEquals(5, entities.size)

        val entityMap = entities.associateBy { it.configTopic }

        // 1. Battery Percentage Sensor
        val batteryTopic = "homeassistant/sensor/batmon_office_pixel_7/battery/config"
        val batteryEntity = entityMap[batteryTopic]
        assertNotNull("Battery entity topic missing", batteryEntity)
        val batteryJson = JSONObject(batteryEntity!!.payloadJson)
        assertEquals("Battery", batteryJson.getString("name"))
        assertEquals("batmon_office_pixel_7_battery", batteryJson.getString("unique_id"))
        assertEquals("batmon/office-pixel-7/state", batteryJson.getString("state_topic"))
        assertEquals("batmon/office-pixel-7/availability", batteryJson.getString("availability_topic"))
        assertEquals("battery", batteryJson.getString("device_class"))
        assertEquals("measurement", batteryJson.getString("state_class"))
        assertEquals("%", batteryJson.getString("unit_of_measurement"))
        assertEquals("{{ value_json.battery_percent }}", batteryJson.getString("value_template"))

        // Verify device block
        val device = batteryJson.getJSONObject("device")
        assertEquals("Office Pixel", device.getString("name"))
        assertEquals("Android Battery Monitor", device.getString("model"))
        assertEquals("BatMon", device.getString("manufacturer"))
        assertEquals("batmon_office_pixel_7", device.getJSONArray("identifiers").getString(0))

        // 2. Charging Binary Sensor
        val chargingTopic = "homeassistant/binary_sensor/batmon_office_pixel_7/charging/config"
        val chargingEntity = entityMap[chargingTopic]
        assertNotNull("Charging entity topic missing", chargingEntity)
        val chargingJson = JSONObject(chargingEntity!!.payloadJson)
        assertEquals("Charging", chargingJson.getString("name"))
        assertEquals("batmon_office_pixel_7_charging", chargingJson.getString("unique_id"))
        assertEquals("battery_charging", chargingJson.getString("device_class"))
        assertEquals("true", chargingJson.getString("payload_on"))
        assertEquals("false", chargingJson.getString("payload_off"))
        assertEquals("{{ value_json.is_charging }}", chargingJson.getString("value_template"))

        // 3. Monitoring Diagnostic Binary Sensor
        val monitoringTopic = "homeassistant/binary_sensor/batmon_office_pixel_7/monitoring/config"
        val monitoringEntity = entityMap[monitoringTopic]
        assertNotNull("Monitoring entity topic missing", monitoringEntity)
        val monitoringJson = JSONObject(monitoringEntity!!.payloadJson)
        assertEquals("diagnostic", monitoringJson.getString("entity_category"))
        assertEquals("{{ value_json.monitoring_enabled }}", monitoringJson.getString("value_template"))

        // 4. Battery Health Sensor
        val healthTopic = "homeassistant/sensor/batmon_office_pixel_7/health/config"
        val healthEntity = entityMap[healthTopic]
        assertNotNull("Health entity topic missing", healthEntity)
        val healthJson = JSONObject(healthEntity!!.payloadJson)
        assertEquals("diagnostic", healthJson.getString("entity_category"))
        assertEquals("mdi:battery-heart-variant", healthJson.getString("icon"))

        // 5. Last Update Sensor
        val lastUpdateTopic = "homeassistant/sensor/batmon_office_pixel_7/last_update/config"
        val lastUpdateEntity = entityMap[lastUpdateTopic]
        assertNotNull("Last update entity topic missing", lastUpdateEntity)
        val lastUpdateJson = JSONObject(lastUpdateEntity!!.payloadJson)
        assertEquals("timestamp", lastUpdateJson.getString("device_class"))
        assertEquals("diagnostic", lastUpdateJson.getString("entity_category"))
        assertEquals("{{ value_json.timestamp }}", lastUpdateJson.getString("value_template"))
    }

    @Test
    fun customTopicPrefix_reflectedInTopics() {
        val settings = BatMonSettings(
            deviceId = "tablet-123",
            mqtt = MqttSettings(
                topicPrefix = "home/tablet",
                discoveryPrefix = "ha"
            )
        )

        assertEquals("home/tablet/state", settings.mqtt.stateTopic(settings.deviceId))
        assertEquals("home/tablet/availability", settings.mqtt.availabilityTopic(settings.deviceId))

        val entities = MqttPayloadFactory.buildDiscoveryEntities(settings)
        for (entity in entities) {
            assertTrue(entity.configTopic.startsWith("ha/"))
            val json = JSONObject(entity.payloadJson)
            assertEquals("home/tablet/state", json.getString("state_topic"))
            assertEquals("home/tablet/availability", json.getString("availability_topic"))
        }
    }

    @Test
    fun buildEventPayload_forLowAlert_containsEventAndThreshold() {
        val battery = BatteryState(
            percentage = 44,
            isCharging = false,
            chargingStatus = "Discharging",
            timestampMs = 1700000000000L
        )
        val settings = BatMonSettings(deviceId = "tablet-office", lowThreshold = 45)

        val jsonString = MqttPayloadFactory.buildEventPayload(
            event = "battery_low",
            batteryState = battery,
            settings = settings,
            thresholdPercent = 45,
            timestampMs = 1700000000000L
        )

        val json = JSONObject(jsonString)
        assertEquals("battery_low", json.getString("event"))
        assertEquals("tablet-office", json.getString("device_id"))
        assertEquals(44, json.getInt("battery_percent"))
        assertEquals(45, json.getInt("threshold_percent"))
        assertEquals(false, json.getBoolean("is_charging"))
        assertEquals("discharging", json.getString("charging_status"))
        assertEquals(Instant.ofEpochMilli(1700000000000L).toString(), json.getString("timestamp"))
    }

    @Test
    fun buildEventPayload_forChargingState_omitsThresholdWhenNull() {
        val battery = BatteryState(
            percentage = 60,
            isCharging = true,
            chargingStatus = "Charging",
            timestampMs = 1700000000000L
        )
        val settings = BatMonSettings(deviceId = "tablet-office")

        val jsonString = MqttPayloadFactory.buildEventPayload(
            event = "charging_started",
            batteryState = battery,
            settings = settings,
            thresholdPercent = null,
            timestampMs = 1700000000000L
        )

        val json = JSONObject(jsonString)
        assertEquals("charging_started", json.getString("event"))
        assertFalse("threshold_percent should be omitted", json.has("threshold_percent"))
        assertEquals(true, json.getBoolean("is_charging"))
    }
}
