package com.batmon.app.webhooks

import org.json.JSONObject

enum class WebhookEvent(val eventKey: String, val displayName: String) {
    BATTERY_LOW("battery_low", "Battery Low"),
    BATTERY_HIGH("battery_high", "Battery High"),
    CHARGING_STARTED("charging_started", "Charging Started"),
    CHARGING_STOPPED("charging_stopped", "Charging Stopped"),
    STATE_UPDATE("state_update", "Battery State Update"),
    MONITORING_WARNING("monitoring_warning", "Monitoring Warning");

    companion object {
        fun fromKey(key: String): WebhookEvent? = entries.find { it.eventKey == key }
    }
}

data class WebhookEventConfig(
    val enabled: Boolean = false,
    val url: String = "",
    val httpMethod: String = "POST",
    val headers: String = "",
    val payloadTemplate: String = "",
    val timeoutSeconds: Int = 10,
    val maxRetries: Int = 3
)

data class WebhookSettings(
    val enabled: Boolean = false,
    val allowInsecureHttp: Boolean = false,
    val lowBatteryConfig: WebhookEventConfig = WebhookEventConfig(enabled = true),
    val highBatteryConfig: WebhookEventConfig = WebhookEventConfig(enabled = true),
    val chargingStartedConfig: WebhookEventConfig = WebhookEventConfig(enabled = false),
    val chargingStoppedConfig: WebhookEventConfig = WebhookEventConfig(enabled = false),
    val stateUpdateConfig: WebhookEventConfig = WebhookEventConfig(enabled = false),
    val monitoringWarningConfig: WebhookEventConfig = WebhookEventConfig(enabled = false)
) {
    fun getConfigForEvent(event: WebhookEvent): WebhookEventConfig = when (event) {
        WebhookEvent.BATTERY_LOW -> lowBatteryConfig
        WebhookEvent.BATTERY_HIGH -> highBatteryConfig
        WebhookEvent.CHARGING_STARTED -> chargingStartedConfig
        WebhookEvent.CHARGING_STOPPED -> chargingStoppedConfig
        WebhookEvent.STATE_UPDATE -> stateUpdateConfig
        WebhookEvent.MONITORING_WARNING -> monitoringWarningConfig
    }
}

fun WebhookEventConfig.toJson(): JSONObject {
    val json = JSONObject()
    json.put("enabled", enabled)
    json.put("url", url)
    json.put("httpMethod", httpMethod)
    json.put("headers", headers)
    json.put("payloadTemplate", payloadTemplate)
    json.put("timeoutSeconds", timeoutSeconds)
    json.put("maxRetries", maxRetries)
    return json
}

fun JSONObject.toWebhookEventConfig(): WebhookEventConfig {
    return WebhookEventConfig(
        enabled = optBoolean("enabled", false),
        url = optString("url", ""),
        httpMethod = optString("httpMethod", "POST"),
        headers = optString("headers", ""),
        payloadTemplate = optString("payloadTemplate", ""),
        timeoutSeconds = optInt("timeoutSeconds", 10),
        maxRetries = optInt("maxRetries", 3)
    )
}

fun WebhookSettings.toJson(): String {
    val json = JSONObject()
    json.put("enabled", enabled)
    json.put("allowInsecureHttp", allowInsecureHttp)
    json.put("lowBattery", lowBatteryConfig.toJson())
    json.put("highBattery", highBatteryConfig.toJson())
    json.put("chargingStarted", chargingStartedConfig.toJson())
    json.put("chargingStopped", chargingStoppedConfig.toJson())
    json.put("stateUpdate", stateUpdateConfig.toJson())
    json.put("monitoringWarning", monitoringWarningConfig.toJson())
    return json.toString()
}

fun String.toWebhookSettings(): WebhookSettings {
    if (isBlank()) return WebhookSettings()
    return try {
        val json = JSONObject(this)
        WebhookSettings(
            enabled = json.optBoolean("enabled", false),
            allowInsecureHttp = json.optBoolean("allowInsecureHttp", false),
            lowBatteryConfig = json.optJSONObject("lowBattery")?.toWebhookEventConfig() ?: WebhookEventConfig(enabled = true),
            highBatteryConfig = json.optJSONObject("highBattery")?.toWebhookEventConfig() ?: WebhookEventConfig(enabled = true),
            chargingStartedConfig = json.optJSONObject("chargingStarted")?.toWebhookEventConfig() ?: WebhookEventConfig(),
            chargingStoppedConfig = json.optJSONObject("chargingStopped")?.toWebhookEventConfig() ?: WebhookEventConfig(),
            stateUpdateConfig = json.optJSONObject("stateUpdate")?.toWebhookEventConfig() ?: WebhookEventConfig(),
            monitoringWarningConfig = json.optJSONObject("monitoringWarning")?.toWebhookEventConfig() ?: WebhookEventConfig()
        )
    } catch (_: Exception) {
        WebhookSettings()
    }
}

enum class DeliveryStatus {
    PENDING,
    IN_FLIGHT,
    SUCCESS,
    FAILED_TRANSIENT,
    FAILED_PERMANENT
}

data class WebhookQueueItem(
    val id: String,
    val event: String,
    val url: String,
    val method: String = "POST",
    val headers: Map<String, String> = emptyMap(),
    val payload: String,
    val createdAtMs: Long,
    val attemptCount: Int = 0,
    val maxRetries: Int = 3,
    val lastAttemptAtMs: Long? = null,
    val lastError: String? = null,
    val responseCode: Int? = null,
    val status: DeliveryStatus = DeliveryStatus.PENDING
)

fun WebhookQueueItem.toJson(): JSONObject {
    val json = JSONObject()
    json.put("id", id)
    json.put("event", event)
    json.put("url", url)
    json.put("method", method)
    val headersJson = JSONObject()
    headers.forEach { (k, v) -> headersJson.put(k, v) }
    json.put("headers", headersJson)
    json.put("payload", payload)
    json.put("createdAtMs", createdAtMs)
    json.put("attemptCount", attemptCount)
    json.put("maxRetries", maxRetries)
    if (lastAttemptAtMs != null) json.put("lastAttemptAtMs", lastAttemptAtMs)
    if (lastError != null) json.put("lastError", lastError)
    if (responseCode != null) json.put("responseCode", responseCode)
    json.put("status", status.name)
    return json
}

fun JSONObject.toWebhookQueueItem(): WebhookQueueItem {
    val headersMap = mutableMapOf<String, String>()
    val headersJson = optJSONObject("headers")
    if (headersJson != null) {
        val keys = headersJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            headersMap[key] = headersJson.optString(key, "")
        }
    }
    return WebhookQueueItem(
        id = optString("id"),
        event = optString("event"),
        url = optString("url"),
        method = optString("method", "POST"),
        headers = headersMap,
        payload = optString("payload"),
        createdAtMs = optLong("createdAtMs"),
        attemptCount = optInt("attemptCount", 0),
        maxRetries = optInt("maxRetries", 3),
        lastAttemptAtMs = if (has("lastAttemptAtMs")) optLong("lastAttemptAtMs") else null,
        lastError = if (has("lastError")) optString("lastError") else null,
        responseCode = if (has("responseCode")) optInt("responseCode") else null,
        status = DeliveryStatus.valueOf(optString("status", DeliveryStatus.PENDING.name))
    )
}
