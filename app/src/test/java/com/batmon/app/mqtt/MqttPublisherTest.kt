package com.batmon.app.mqtt

import com.batmon.app.battery.BatteryState
import com.batmon.app.data.BatMonSettings
import com.batmon.app.data.MqttSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttPublisherTest {

    @Test
    fun publishState_whenDisabled_returnsSuccessWithoutConnecting() = runBlocking {
        val publisher = MqttPublisher()
        val battery = BatteryState(
            percentage = 50,
            isCharging = false,
            chargingStatus = "Discharging",
            timestampMs = 1000L
        )
        val settings = BatMonSettings(mqtt = MqttSettings(enabled = false))

        val result = publisher.publishState(battery, settings)
        assertTrue(result.isSuccess)
        assertEquals(MqttConnectionStatus.Disconnected, publisher.status.value)
    }

    @Test
    fun publishState_whenHostBlank_returnsSuccessWithoutConnecting() = runBlocking {
        val publisher = MqttPublisher()
        val battery = BatteryState(
            percentage = 50,
            isCharging = false,
            chargingStatus = "Discharging",
            timestampMs = 1000L
        )
        val settings = BatMonSettings(mqtt = MqttSettings(enabled = true, host = ""))

        val result = publisher.publishState(battery, settings)
        assertTrue(result.isSuccess)
        assertEquals(MqttConnectionStatus.Disconnected, publisher.status.value)
    }

    @Test
    fun testConnection_withBlankHost_failsWithDescriptiveError() = runBlocking {
        val publisher = MqttPublisher()
        val settings = BatMonSettings(mqtt = MqttSettings(enabled = true, host = ""))

        val result = publisher.testConnection(settings)
        assertTrue(result.isFailure)
        assertEquals("Broker host cannot be blank", result.exceptionOrNull()?.message)
    }

    @Test
    fun testConnection_withUnreachableHost_redactsPasswordInErrorMessage() = runBlocking {
        val publisher = MqttPublisher()
        val secretPassword = "SuperSecretPassword123!"
        val settings = BatMonSettings(
            mqtt = MqttSettings(
                enabled = true,
                host = "127.0.0.1",
                port = 19999, // Unused port, will immediately fail connection
                password = secretPassword
            )
        )

        val result = publisher.testConnection(settings)
        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message ?: ""
        assertFalse(
            "Error message must never contain the plaintext password",
            errorMsg.contains(secretPassword)
        )
    }

    @Test
    fun testConnection_redactsPasswordOnFailure() = runBlocking {
        val publisher = MqttPublisher()
        val secretPassword = "Mqtt5Password999!"
        val settings = BatMonSettings(
            mqtt = MqttSettings(
                enabled = true,
                host = "127.0.0.1",
                port = 19998,
                password = secretPassword
            )
        )

        val result = publisher.testConnection(settings)
        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message ?: ""
        assertFalse(
            "Error message must never contain the plaintext password in MQTT 5",
            errorMsg.contains(secretPassword)
        )
    }

    @Test
    fun publishEvent_whenDisabled_returnsSuccessWithoutConnecting() = runBlocking {
        val publisher = MqttPublisher()
        val battery = BatteryState(
            percentage = 40,
            isCharging = false,
            chargingStatus = "Discharging",
            timestampMs = 1000L
        )
        val settings = BatMonSettings(mqtt = MqttSettings(enabled = false))

        val result = publisher.publishEvent("battery_low", battery, settings, 45)
        assertTrue(result.isSuccess)
        assertEquals(MqttConnectionStatus.Disconnected, publisher.status.value)
    }

    @Test
    fun effectiveTopicPrefix_whenBlank_defaultsToBatmonWithDeviceId() {
        val mqtt = MqttSettings(topicPrefix = "")
        val prefix = mqtt.effectiveTopicPrefix("pixel_tablet")
        assertEquals("batmon/pixel_tablet", prefix)
        assertEquals("batmon/pixel_tablet/state", mqtt.stateTopic("pixel_tablet"))
        assertEquals("batmon/pixel_tablet/event", mqtt.eventTopic("pixel_tablet"))
        assertEquals("batmon/pixel_tablet/availability", mqtt.availabilityTopic("pixel_tablet"))
    }

    @Test
    fun effectiveTopicPrefix_whenCustom_usesCustomPrefix() {
        val mqtt = MqttSettings(topicPrefix = "home/battery/tablet1")
        val prefix = mqtt.effectiveTopicPrefix("tablet1")
        assertEquals("home/battery/tablet1", prefix)
        assertEquals("home/battery/tablet1/state", mqtt.stateTopic("tablet1"))
        assertEquals("home/battery/tablet1/event", mqtt.eventTopic("tablet1"))
        assertEquals("home/battery/tablet1/availability", mqtt.availabilityTopic("tablet1"))
    }

    @Test
    fun effectiveTopicPrefix_whenTrailingSlash_trimsTrailingSlash() {
        val mqtt = MqttSettings(topicPrefix = "batmon/tablet1/")
        val prefix = mqtt.effectiveTopicPrefix("tablet1")
        assertEquals("batmon/tablet1", prefix)
        assertEquals("batmon/tablet1/state", mqtt.stateTopic("tablet1"))
    }

    @Test
    fun effectiveClientId_autogeneratesCleanUniqueClientIdFromDeviceId() {
        val mqtt = MqttSettings()
        assertEquals("batmon_7f3a91c2", mqtt.effectiveClientId("batmon-7f3a91c2"))
        assertEquals("batmon_tablet1", mqtt.effectiveClientId("batmon_tablet1"))
        assertEquals("batmon_kitchen_pixel", mqtt.effectiveClientId("kitchen_pixel"))
        assertEquals("batmon_office_wall", mqtt.effectiveClientId("office_wall"))
    }

    @Test
    fun testConnection_withCustomBatteryStateAndUnreachableHost_failsSafely() = runBlocking {
        val publisher = MqttPublisher()
        val battery = BatteryState(percentage = 85, isCharging = true, chargingStatus = "Charging", timestampMs = 5000L)
        val settings = BatMonSettings(
            mqtt = MqttSettings(enabled = true, host = "127.0.0.1", port = 19999, password = "secret")
        )
        val result = publisher.testConnection(settings, battery)
        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull()?.message?.contains("secret") ?: false)
    }
}
