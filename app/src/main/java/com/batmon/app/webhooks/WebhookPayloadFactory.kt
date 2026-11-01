package com.batmon.app.webhooks

import com.batmon.app.battery.BatteryState
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object WebhookUrlValidator {
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class InsecureHttp(val message: String) : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
    }

    fun validate(url: String, allowInsecureHttp: Boolean): ValidationResult {
        if (url.isBlank()) return ValidationResult.Invalid("URL cannot be empty")
        val httpUrl = url.toHttpUrlOrNull() ?: return ValidationResult.Invalid("Invalid URL format")
        if (httpUrl.scheme != "https" && httpUrl.scheme != "http") {
            return ValidationResult.Invalid("Only HTTP and HTTPS URLs are supported")
        }
        if (httpUrl.host.isBlank()) {
            return ValidationResult.Invalid("Host cannot be empty")
        }
        if (httpUrl.scheme == "http" && !allowInsecureHttp) {
            return ValidationResult.InsecureHttp("Insecure HTTP destination. HTTPS is required unless insecure HTTP is explicitly enabled.")
        }
        return ValidationResult.Valid
    }
}

object WebhookPayloadFactory {

    fun formatUtcIso8601(timestampMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestampMs))
    }

    fun buildPayload(
        event: WebhookEvent,
        batteryState: BatteryState,
        deviceId: String,
        deviceName: String,
        thresholdPercent: Int? = null,
        template: String = "",
        eventId: String = UUID.randomUUID().toString(),
        timestampMs: Long = System.currentTimeMillis()
    ): String {
        val isoTimestamp = formatUtcIso8601(timestampMs)

        if (template.isNotBlank()) {
            return template
                .replace("{event}", event.eventKey)
                .replace("{event_id}", eventId)
                .replace("{device_id}", deviceId)
                .replace("{device_name}", deviceName)
                .replace("{battery_percent}", batteryState.percentage.toString())
                .replace("{threshold_percent}", (thresholdPercent ?: batteryState.percentage).toString())
                .replace("{is_charging}", batteryState.isCharging.toString())
                .replace("{charging_status}", batteryState.chargingStatus.lowercase(Locale.ROOT))
                .replace("{timestamp}", isoTimestamp)
        }

        val json = JSONObject()
        json.put("event", event.eventKey)
        json.put("event_id", eventId)
        json.put("device_id", deviceId)
        json.put("device_name", deviceName)
        json.put("battery_percent", batteryState.percentage)
        if (thresholdPercent != null) {
            json.put("threshold_percent", thresholdPercent)
        }
        json.put("charging_status", batteryState.chargingStatus.lowercase(Locale.ROOT))
        json.put("is_charging", batteryState.isCharging)
        if (batteryState.health != null) {
            json.put("health", batteryState.health)
        }
        if (batteryState.temperatureCelsius != null) {
            json.put("temperature_c", batteryState.temperatureCelsius.toDouble())
        }
        if (batteryState.pluggedSource != null) {
            json.put("plugged_source", batteryState.pluggedSource)
        }
        json.put("timestamp", isoTimestamp)
        return json.toString()
    }

    fun parseHeaders(headersText: String): Map<String, String> {
        if (headersText.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        headersText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && trimmed.contains(":")) {
                val idx = trimmed.indexOf(":")
                val key = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim()
                if (key.isNotEmpty()) {
                    result[key] = value
                }
            }
        }
        return result
    }

    fun sanitizeHeadersForLogging(headers: Map<String, String>): Map<String, String> {
        val sensitiveKeys = setOf("authorization", "proxy-authorization", "x-api-key", "token", "secret", "password")
        return headers.mapValues { (key, value) ->
            if (sensitiveKeys.contains(key.lowercase(Locale.ROOT))) {
                "***REDACTED***"
            } else {
                value
            }
        }
    }
}
