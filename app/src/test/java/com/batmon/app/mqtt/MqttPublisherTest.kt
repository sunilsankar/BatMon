package com.batmon.app.mqtt

import com.batmon.app.battery.BatteryState
import com.batmon.app.data.BatMonSettings
import com.batmon.app.data.MqttSettings
import kotlinx.coroutines.runBlocking
import org.eclipse.paho.client.mqttv3.MqttException
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
    fun testConnection_withUnreachableHost_failsWithPreFlightSocketError() = runBlocking {
        val publisher = MqttPublisher()
        val secretPassword = "SuperSecretPassword123!"
        val settings = BatMonSettings(
            mqtt = MqttSettings(
                enabled = true,
                host = "127.0.0.1",
                port = 19999, // Unused port, fails raw TCP socket check
                username = "batmon_user",
                password = secretPassword
            )
        )

        val result = publisher.testConnection(settings)
        assertTrue(result.isFailure)
        val errorMsg = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Error message should indicate unreachable socket: $errorMsg",
            errorMsg.startsWith("Cannot reach 127.0.0.1:19999")
        )
        assertFalse(
            "Error message must never contain the plaintext password",
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
    fun deviceShortId_stripsPrefixAndSanitizes() {
        val mqtt = MqttSettings()
        assertEquals("7f3a91c2", mqtt.deviceShortId("batmon-7f3a91c2"))
        assertEquals("tablet1", mqtt.deviceShortId("batmon_tablet1"))
        assertEquals("kitchen_pixel", mqtt.deviceShortId("kitchen_pixel"))
        assertEquals("office_wall", mqtt.deviceShortId("office-wall"))
    }

    @Test
    fun effectiveTopicPrefix_andTopics_followDsmr2MqttScheme() {
        val mqtt = MqttSettings(topicPrefix = "")
        val prefix = mqtt.effectiveTopicPrefix()
        assertEquals("batmon", prefix)
        assertEquals("batmon_tablet1/status", mqtt.statusTopic("tablet1"))
        assertEquals("batmon_tablet1/status", mqtt.availabilityTopic("tablet1"))
        assertEquals("batmon_tablet1/battery_level", mqtt.metricTopic("tablet1", "battery_level"))
        assertEquals("homeassistant/sensor/batmon_tablet1/battery_level/config", mqtt.discoveryTopic("tablet1", "battery_level"))
        assertEquals("batmon_tablet1/state", mqtt.stateTopic("tablet1"))
        assertEquals("batmon_tablet1/event", mqtt.eventTopic("tablet1"))
    }

    @Test
    fun effectiveTopicPrefix_whenCustom_usesCustomPrefix() {
        val mqtt = MqttSettings(topicPrefix = "dsmr")
        val prefix = mqtt.effectiveTopicPrefix()
        assertEquals("dsmr", prefix)
        assertEquals("dsmr_tablet1/status", mqtt.statusTopic("tablet1"))
        assertEquals("dsmr_tablet1/battery_level", mqtt.metricTopic("tablet1", "battery_level"))
        assertEquals("homeassistant/sensor/dsmr_tablet1/battery_level/config", mqtt.discoveryTopic("tablet1", "battery_level"))
    }

    @Test
    fun effectiveTopicPrefix_whenContainsSlash_resetsToDefault() {
        val mqtt = MqttSettings(topicPrefix = "batmon/tablet1/")
        assertEquals("batmon", mqtt.effectiveTopicPrefix())
    }

    @Test
    fun effectiveClientId_generatesPrefixedClientId() {
        val mqtt = MqttSettings(topicPrefix = "batmon")
        assertEquals("mqtt-batmon-7f3a91c2", mqtt.effectiveClientId("batmon-7f3a91c2"))
        assertEquals("mqtt-batmon-tablet1", mqtt.effectiveClientId("batmon_tablet1"))
    }

    @Test
    fun sanitizeError_mapsAuthFailureCodes4And5() {
        val publisher = MqttPublisher()
        val authEx4 = MqttException(MqttException.REASON_CODE_FAILED_AUTHENTICATION.toInt())
        val authEx5 = MqttException(MqttException.REASON_CODE_NOT_AUTHORIZED.toInt())
        val timeoutEx = MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT.toInt())

        val msg4 = publisher.sanitizeError(
            e = authEx4,
            password = "secret_password",
            username = "homeassistant",
            clientId = "mqtt-batmon-test"
        )
        assertTrue(msg4.contains("rc 4"))
        assertTrue(msg4.contains("user: \"homeassistant\""))
        assertTrue(msg4.contains("password: 15 chars"))
        assertFalse(msg4.contains("secret_password"))

        val msg5 = publisher.sanitizeError(
            e = authEx5,
            password = "secret_password",
            username = "homeassistant",
            clientId = "mqtt-batmon-test",
            anonymousProbeResult = true
        )
        assertTrue(msg5.contains("rc 5"))
        assertTrue(msg5.contains("not authorised"))
        assertTrue(msg5.contains("Probe: broker permits anonymous"))
        assertFalse(msg5.contains("secret_password"))

        val msg5AnonRefused = publisher.sanitizeError(
            e = authEx5,
            password = "secret_password",
            username = "homeassistant",
            clientId = "mqtt-batmon-test",
            anonymousProbeResult = false
        )
        assertTrue(msg5AnonRefused.contains("Probe: anonymous also refused"))

        assertTrue(
            publisher.sanitizeError(timeoutEx, "my_pw").contains("Timed out")
        )
    }
}
