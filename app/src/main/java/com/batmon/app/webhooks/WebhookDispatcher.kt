package com.batmon.app.webhooks

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

class WebhookDispatcher(
    private val queueRepository: WebhookQueueRepository,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "WebhookDispatcher"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun calculateBackoffDelayMs(attemptCount: Int, baseDelayMs: Long = 1000L, maxDelayMs: Long = 60_000L): Long {
            val exp = 2.0.pow(attemptCount.coerceAtLeast(0).toDouble()).toLong()
            return min(baseDelayMs * exp, maxDelayMs)
        }

        fun sanitizeError(message: String?): String {
            if (message == null) return "Unknown error"
            // Redact credentials, tokens, bearer patterns
            return message
                .replace(Regex("(?i)bearer\\s+[a-zA-Z0-9._~+/-]+"), "Bearer ***REDACTED***")
                .replace(Regex("(?i)password=[^&\\s]+"), "password=***REDACTED***")
        }
    }

    suspend fun dispatch(item: WebhookQueueItem, allowInsecureHttp: Boolean = false): WebhookQueueItem {
        val validation = WebhookUrlValidator.validate(item.url, allowInsecureHttp)
        if (validation !is WebhookUrlValidator.ValidationResult.Valid) {
            val errorMsg = when (validation) {
                is WebhookUrlValidator.ValidationResult.InsecureHttp -> validation.message
                is WebhookUrlValidator.ValidationResult.Invalid -> validation.message
                else -> "URL validation failed"
            }
            val completed = item.copy(
                attemptCount = item.attemptCount + 1,
                lastAttemptAtMs = System.currentTimeMillis(),
                lastError = errorMsg,
                status = DeliveryStatus.FAILED_PERMANENT
            )
            queueRepository.recordCompletion(completed)
            return completed
        }

        val requestBuilder = Request.Builder()
            .url(item.url)
            .addHeader("X-Event-ID", item.id)

        // Add custom headers
        item.headers.forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
        }

        val body = item.payload.toRequestBody(JSON_MEDIA_TYPE)
        when (item.method.uppercase()) {
            "PUT" -> requestBuilder.put(body)
            else -> requestBuilder.post(body)
        }

        val nextAttempt = item.attemptCount + 1
        val now = System.currentTimeMillis()

        return try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val code = response.code
            response.close()

            when {
                code in 200..299 -> {
                    val completed = item.copy(
                        attemptCount = nextAttempt,
                        lastAttemptAtMs = now,
                        responseCode = code,
                        lastError = null,
                        status = DeliveryStatus.SUCCESS
                    )
                    queueRepository.recordCompletion(completed)
                    completed
                }
                code in 400..499 -> {
                    // Permanent client error — do not retry indefinitely
                    val completed = item.copy(
                        attemptCount = nextAttempt,
                        lastAttemptAtMs = now,
                        responseCode = code,
                        lastError = "HTTP $code: Client error (not retrying)",
                        status = DeliveryStatus.FAILED_PERMANENT
                    )
                    queueRepository.recordCompletion(completed)
                    completed
                }
                else -> {
                    // 5xx Server error — transient failure
                    val isExhausted = nextAttempt >= item.maxRetries
                    val updated = item.copy(
                        attemptCount = nextAttempt,
                        lastAttemptAtMs = now,
                        responseCode = code,
                        lastError = "HTTP $code: Server error" + if (isExhausted) " (retries exhausted)" else "",
                        status = if (isExhausted) DeliveryStatus.FAILED_PERMANENT else DeliveryStatus.FAILED_TRANSIENT
                    )
                    if (isExhausted) {
                        queueRepository.recordCompletion(updated)
                    } else {
                        queueRepository.updatePending(updated)
                    }
                    updated
                }
            }
        } catch (e: IOException) {
            val isExhausted = nextAttempt >= item.maxRetries
            val cleanError = sanitizeError(e.message ?: "Network connection failed")
            val updated = item.copy(
                attemptCount = nextAttempt,
                lastAttemptAtMs = now,
                responseCode = null,
                lastError = "Network error: $cleanError" + if (isExhausted) " (retries exhausted)" else "",
                status = if (isExhausted) DeliveryStatus.FAILED_PERMANENT else DeliveryStatus.FAILED_TRANSIENT
            )
            if (isExhausted) {
                queueRepository.recordCompletion(updated)
            } else {
                queueRepository.updatePending(updated)
            }
            updated
        }
    }

    suspend fun processPendingQueue(allowInsecureHttp: Boolean = false): Int {
        val pending = queueRepository.getPendingItems()
        var processedCount = 0
        for (item in pending) {
            val result = dispatch(item, allowInsecureHttp)
            processedCount++
            if (result.status == DeliveryStatus.FAILED_TRANSIENT) {
                // If network/server error occurred on one item, don't spam the remaining items
                Log.d(TAG, "Transient failure encountered for ${item.id}, postponing queue processing")
                break
            }
        }
        return processedCount
    }

    suspend fun enqueueAndProcess(
        event: WebhookEvent,
        battery: com.batmon.app.battery.BatteryState,
        settings: com.batmon.app.data.BatMonSettings,
        thresholdPercent: Int? = null
    ) {
        if (!settings.webhooks.enabled) return
        val config = settings.webhooks.getConfigForEvent(event)
        if (!config.enabled || config.url.isBlank()) return

        val payload = WebhookPayloadFactory.buildPayload(
            event = event,
            batteryState = battery,
            deviceId = settings.deviceId,
            deviceName = settings.deviceName,
            thresholdPercent = thresholdPercent,
            template = config.payloadTemplate
        )

        val headers = WebhookPayloadFactory.parseHeaders(config.headers)
        val item = WebhookQueueItem(
            id = java.util.UUID.randomUUID().toString(),
            event = event.eventKey,
            url = config.url,
            method = config.httpMethod,
            headers = headers,
            payload = payload,
            createdAtMs = System.currentTimeMillis(),
            maxRetries = config.maxRetries
        )
        queueRepository.enqueue(item)
        processPendingQueue(settings.webhooks.allowInsecureHttp)
    }

    suspend fun testDelivery(
        url: String,
        method: String,
        headers: String,
        payload: String,
        allowInsecureHttp: Boolean,
        timeoutSeconds: Int = 10
    ): Result<Int> {
        val validation = WebhookUrlValidator.validate(url, allowInsecureHttp)
        if (validation !is WebhookUrlValidator.ValidationResult.Valid) {
            val msg = when (validation) {
                is WebhookUrlValidator.ValidationResult.InsecureHttp -> validation.message
                is WebhookUrlValidator.ValidationResult.Invalid -> validation.message
                else -> "URL validation failed"
            }
            return Result.failure(IllegalArgumentException(msg))
        }

        val client = okHttpClient.newBuilder()
            .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()

        val parsedHeaders = WebhookPayloadFactory.parseHeaders(headers)
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("X-Event-ID", "test-${System.currentTimeMillis()}")

        parsedHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        val body = payload.toRequestBody(JSON_MEDIA_TYPE)
        when (method.uppercase()) {
            "PUT" -> requestBuilder.put(body)
            else -> requestBuilder.post(body)
        }

        return try {
            val response = client.newCall(requestBuilder.build()).execute()
            val code = response.code
            response.close()
            Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
