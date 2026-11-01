package com.batmon.app.webhooks

import com.batmon.app.battery.BatteryState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookPayloadFactoryTest {

    private val sampleBattery = BatteryState(
        percentage = 42,
        isCharging = false,
        chargingStatus = "Discharging",
        pluggedSource = "None",
        health = "Good",
        temperatureCelsius = 25.4f,
        timestampMs = 1700000000000L
    )

    @Test
    fun buildPayload_defaultJson_containsExpectedFields() {
        val payload = WebhookPayloadFactory.buildPayload(
            event = WebhookEvent.BATTERY_LOW,
            batteryState = sampleBattery,
            deviceId = "test-tablet-01",
            deviceName = "Office Tablet",
            thresholdPercent = 45
        )

        val json = JSONObject(payload)
        assertEquals("battery_low", json.getString("event"))
        assertTrue(json.has("event_id"))
        assertEquals("test-tablet-01", json.getString("device_id"))
        assertEquals("Office Tablet", json.getString("device_name"))
        assertEquals(42, json.getInt("battery_percent"))
        assertEquals(45, json.getInt("threshold_percent"))
        assertFalse(json.getBoolean("is_charging"))
        assertEquals("discharging", json.getString("charging_status"))
        assertEquals("Good", json.getString("health"))
        assertEquals(25.4, json.getDouble("temperature_c"), 0.01)
        assertTrue(json.has("timestamp"))
    }

    @Test
    fun buildPayload_withCustomTemplate_replacesPlaceholders() {
        val template = """{"kind":"{event}","target":"{device_id}","pct":{battery_percent},"thr":{threshold_percent},"id":"{event_id}"}"""
        val payload = WebhookPayloadFactory.buildPayload(
            event = WebhookEvent.BATTERY_HIGH,
            batteryState = sampleBattery.copy(percentage = 85, isCharging = true, chargingStatus = "Charging"),
            deviceId = "pixel-tablet",
            deviceName = "Kitchen Pixel",
            thresholdPercent = 80,
            template = template
        )

        val json = JSONObject(payload)
        assertEquals("battery_high", json.getString("kind"))
        assertEquals("pixel-tablet", json.getString("target"))
        assertEquals(85, json.getInt("pct"))
        assertEquals(80, json.getInt("thr"))
        assertTrue(json.getString("id").isNotBlank())
    }

    @Test
    fun parseHeaders_parsesLinesCorrectly() {
        val headerText = """
            Content-Type: application/json
            Authorization: Bearer secret-token-xyz
            X-Custom-Header: value:with:colons
            
            # This is whitespace
        """.trimIndent()

        val headers = WebhookPayloadFactory.parseHeaders(headerText)
        assertEquals(3, headers.size)
        assertEquals("application/json", headers["Content-Type"])
        assertEquals("Bearer secret-token-xyz", headers["Authorization"])
        assertEquals("value:with:colons", headers["X-Custom-Header"])
    }

    @Test
    fun sanitizeHeadersForLogging_redactsSensitiveValues() {
        val headers = mapOf(
            "Authorization" to "Bearer sensitive_token_abc",
            "X-Api-Key" to "super-secret-key-123",
            "Content-Type" to "application/json"
        )

        val sanitized = WebhookPayloadFactory.sanitizeHeadersForLogging(headers)
        assertEquals("***REDACTED***", sanitized["Authorization"])
        assertEquals("***REDACTED***", sanitized["X-Api-Key"])
        assertEquals("application/json", sanitized["Content-Type"])
    }

    @Test
    fun urlValidator_validatesCorrectly() {
        // Valid HTTPS
        assertTrue(WebhookUrlValidator.validate("https://homeassistant.local:8123/api/webhook/batmon", false) is WebhookUrlValidator.ValidationResult.Valid)
        assertTrue(WebhookUrlValidator.validate("https://example.com/api?key=val", false) is WebhookUrlValidator.ValidationResult.Valid)

        // Insecure HTTP blocked by default
        val httpBlocked = WebhookUrlValidator.validate("http://192.168.1.100:8123/api/webhook", false)
        assertTrue(httpBlocked is WebhookUrlValidator.ValidationResult.InsecureHttp)

        // Insecure HTTP allowed when explicitly enabled
        val httpAllowed = WebhookUrlValidator.validate("http://192.168.1.100:8123/api/webhook", true)
        assertTrue(httpAllowed is WebhookUrlValidator.ValidationResult.Valid)

        // Invalid URLs
        assertTrue(WebhookUrlValidator.validate("", false) is WebhookUrlValidator.ValidationResult.Invalid)
        assertTrue(WebhookUrlValidator.validate("   ", false) is WebhookUrlValidator.ValidationResult.Invalid)
        assertTrue(WebhookUrlValidator.validate("not a url", false) is WebhookUrlValidator.ValidationResult.Invalid)
        assertTrue(WebhookUrlValidator.validate("ftp://my-server.com", false) is WebhookUrlValidator.ValidationResult.Invalid)
    }

    @Test
    fun webhookEvents_allSixEvents_configuredCorrectly() {
        val allEvents = WebhookEvent.entries
        assertEquals(6, allEvents.size)
        assertTrue(allEvents.contains(WebhookEvent.BATTERY_LOW))
        assertTrue(allEvents.contains(WebhookEvent.BATTERY_HIGH))
        assertTrue(allEvents.contains(WebhookEvent.CHARGING_STARTED))
        assertTrue(allEvents.contains(WebhookEvent.CHARGING_STOPPED))
        assertTrue(allEvents.contains(WebhookEvent.STATE_UPDATE))
        assertTrue(allEvents.contains(WebhookEvent.MONITORING_WARNING))

        val settings = WebhookSettings()
        // Threshold alerts enabled by default
        assertTrue(settings.getConfigForEvent(WebhookEvent.BATTERY_LOW).enabled)
        assertTrue(settings.getConfigForEvent(WebhookEvent.BATTERY_HIGH).enabled)
        // Secondary alerts disabled by default
        assertFalse(settings.getConfigForEvent(WebhookEvent.CHARGING_STARTED).enabled)
        assertFalse(settings.getConfigForEvent(WebhookEvent.CHARGING_STOPPED).enabled)
        assertFalse(settings.getConfigForEvent(WebhookEvent.STATE_UPDATE).enabled)
        assertFalse(settings.getConfigForEvent(WebhookEvent.MONITORING_WARNING).enabled)
    }
}
