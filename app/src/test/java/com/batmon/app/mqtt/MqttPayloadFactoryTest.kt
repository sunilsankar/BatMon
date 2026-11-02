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
    fun buildDiscoveryEntities_matchesDsmr2MqttSchema() {
        val settings = BatMonSettings(
            deviceId = "office-pixel-7",
            deviceName = "Office Pixel",
            mqtt = MqttSettings(
                topicPrefix = "batmon"
            )
        )

        val entities = MqttPayloadFactory.buildDiscoveryEntities(settings)
        assertEquals(6, entities.size)

        val entityMap = entities.associateBy { it.configTopic }

        // 1. Battery Level Sensor
        val batteryTopic = "homeassistant/sensor/batmon_office_pixel_7/battery_level/config"
        val batteryEntity = entityMap[batteryTopic]
        assertNotNull("Battery entity topic missing", batteryEntity)
        val batteryJson = JSONObject(batteryEntity!!.payloadJson)
        assertEquals("Battery Level", batteryJson.getString("name"))
        assertEquals("batmon_office_pixel_7_battery_level", batteryJson.getString("uniq_id"))
        assertEquals("batmon_office_pixel_7/battery_level", batteryJson.getString("stat_t"))
        assertEquals("batmon_office_pixel_7/status", batteryJson.getString("avty_t"))
        assertEquals("online", batteryJson.getString("pl_avail"))
        assertEquals("offline", batteryJson.getString("pl_not_avail"))
        assertEquals("battery", batteryJson.getString("dev_cla"))
        assertEquals("measurement", batteryJson.getString("stat_cla"))
        assertEquals("%", batteryJson.getString("unit_of_meas"))

        // Verify device block
        val device = batteryJson.getJSONObject("dev")
        assertTrue(device.getString("name").contains("Office Pixel"))
        assertEquals("Office Pixel", device.getString("mdl"))
        assertEquals("Android Batmon", device.getString("mf"))
        assertEquals("batmon_office_pixel_7", device.getJSONArray("ids").getString(0))

        // 2. Charging Status Sensor (string state -> no stat_cla)
        val chargingStatusTopic = "homeassistant/sensor/batmon_office_pixel_7/charging_status/config"
        val chargingStatusEntity = entityMap[chargingStatusTopic]
        assertNotNull("Charging status topic missing", chargingStatusEntity)
        val chargingStatusJson = JSONObject(chargingStatusEntity!!.payloadJson)
        assertEquals("Charging Status", chargingStatusJson.getString("name"))
        assertEquals("batmon_office_pixel_7_charging_status", chargingStatusJson.getString("uniq_id"))
        assertEquals("batmon_office_pixel_7/charging_status", chargingStatusJson.getString("stat_t"))
        assertFalse("String sensor must not have stat_cla", chargingStatusJson.has("stat_cla"))

        // 3. Charging Binary Sensor
        val chargingTopic = "homeassistant/binary_sensor/batmon_office_pixel_7/is_charging/config"
        val chargingEntity = entityMap[chargingTopic]
        assertNotNull("Charging binary sensor topic missing", chargingEntity)
        val chargingJson = JSONObject(chargingEntity!!.payloadJson)
        assertEquals("Charging", chargingJson.getString("name"))
        assertEquals("batmon_office_pixel_7_is_charging", chargingJson.getString("uniq_id"))
        assertEquals("battery_charging", chargingJson.getString("dev_cla"))
        assertEquals("online", chargingJson.getString("pl_avail"))
        assertEquals("offline", chargingJson.getString("pl_not_avail"))
        assertEquals("ON", chargingJson.getString("pl_on"))
        assertEquals("OFF", chargingJson.getString("pl_off"))
        assertEquals("batmon_office_pixel_7/is_charging", chargingJson.getString("stat_t"))

        // 4. Temperature Sensor
        val tempTopic = "homeassistant/sensor/batmon_office_pixel_7/temperature/config"
        val tempEntity = entityMap[tempTopic]
        assertNotNull("Temperature topic missing", tempEntity)
        val tempJson = JSONObject(tempEntity!!.payloadJson)
        assertEquals("temperature", tempJson.getString("dev_cla"))
        assertEquals("measurement", tempJson.getString("stat_cla"))
        assertEquals("°C", tempJson.getString("unit_of_meas"))

        // 5. Health Sensor
        val healthTopic = "homeassistant/sensor/batmon_office_pixel_7/health/config"
        val healthEntity = entityMap[healthTopic]
        assertNotNull("Health topic missing", healthEntity)
        val healthJson = JSONObject(healthEntity!!.payloadJson)
        assertEquals("diagnostic", healthJson.getString("ent_cat"))

        // 6. Last Update Sensor
        val lastUpdateTopic = "homeassistant/sensor/batmon_office_pixel_7/last_update/config"
        val lastUpdateEntity = entityMap[lastUpdateTopic]
        assertNotNull("Last update topic missing", lastUpdateEntity)
        val lastUpdateJson = JSONObject(lastUpdateEntity!!.payloadJson)
        assertEquals("timestamp", lastUpdateJson.getString("dev_cla"))
        assertEquals("diagnostic", lastUpdateJson.getString("ent_cat"))
    }

    @Test
    fun buildMetricReadings_providesScalarValues() {
        val battery = BatteryState(
            percentage = 72,
            isCharging = false,
            chargingStatus = "Discharging",
            pluggedSource = "Unplugged",
            temperatureCelsius = 26.5f,
            health = "Good",
            timestampMs = 1700000000000L
        )

        val readings = MqttPayloadFactory.buildMetricReadings(battery, 1700000000000L)
        assertEquals("72", readings["battery_level"])
        assertEquals("discharging", readings["charging_status"])
        assertEquals("OFF", readings["is_charging"])
        assertEquals("Good", readings["health"])
        assertEquals("26.5", readings["temperature"])
        assertEquals(Instant.ofEpochMilli(1700000000000L).toString(), readings["last_update"])
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
