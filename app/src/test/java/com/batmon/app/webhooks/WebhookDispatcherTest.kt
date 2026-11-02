package com.batmon.app.webhooks

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class WebhookDispatcherTest {

    private lateinit var server: MockWebServer
    private lateinit var queueRepository: WebhookQueueRepository
    private lateinit var dispatcher: WebhookDispatcher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        queueRepository = WebhookQueueRepository(context = null)
        dispatcher = WebhookDispatcher(queueRepository)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createItem(
        url: String = server.url("/webhook").toString(),
        attemptCount: Int = 0,
        maxRetries: Int = 3
    ): WebhookQueueItem {
        return WebhookQueueItem(
            id = UUID.randomUUID().toString(),
            event = "battery_low",
            url = url,
            method = "POST",
            headers = mapOf("Content-Type" to "application/json", "X-Test" to "batmon"),
            payload = """{"event":"battery_low","percent":40}""",
            createdAtMs = System.currentTimeMillis(),
            attemptCount = attemptCount,
            maxRetries = maxRetries
        )
    }

    @Test
    fun dispatch_success200_recordsSuccessAndMovesFromQueueToHistory() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"ok"}"""))
        val item = createItem()
        queueRepository.enqueue(item)

        val result = dispatcher.dispatch(item, allowInsecureHttp = true)

        assertEquals(DeliveryStatus.SUCCESS, result.status)
        assertEquals(200, result.responseCode)
        assertEquals(1, result.attemptCount)
        assertNull(result.lastError)

        // Verifies queue updated
        assertEquals(0, queueRepository.getPendingItems().size)
        val history = queueRepository.getHistory()
        assertEquals(1, history.size)
        assertEquals(result.id, history[0].id)
        assertEquals(DeliveryStatus.SUCCESS, history[0].status)

        // Verifies network request details
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(item.id, recorded.getHeader("X-Event-ID"))
        assertEquals("batmon", recorded.getHeader("X-Test"))
        assertEquals(item.payload, recorded.body.readUtf8())
    }

    @Test
    fun dispatch_permanentFailure404_recordsPermanentFailureAndDoesNotRemainInPendingQueue() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        val item = createItem()
        queueRepository.enqueue(item)

        val result = dispatcher.dispatch(item, allowInsecureHttp = true)

        assertEquals(DeliveryStatus.FAILED_PERMANENT, result.status)
        assertEquals(404, result.responseCode)
        assertEquals(1, result.attemptCount)
        assertTrue(result.lastError?.contains("404") == true)

        // Should be removed from pending queue and placed in history
        assertEquals(0, queueRepository.getPendingItems().size)
        val history = queueRepository.getHistory()
        assertEquals(1, history.size)
        assertEquals(DeliveryStatus.FAILED_PERMANENT, history[0].status)
    }

    @Test
    fun dispatch_transientFailure500_onFirstAttempt_keepsInPendingQueueForRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
        val item = createItem(maxRetries = 3)
        queueRepository.enqueue(item)

        val result = dispatcher.dispatch(item, allowInsecureHttp = true)

        assertEquals(DeliveryStatus.FAILED_TRANSIENT, result.status)
        assertEquals(500, result.responseCode)
        assertEquals(1, result.attemptCount)

        // Should remain in pending queue for retry
        val pending = queueRepository.getPendingItems()
        assertEquals(1, pending.size)
        assertEquals(1, pending[0].attemptCount)
        assertEquals(DeliveryStatus.FAILED_TRANSIENT, pending[0].status)
        assertEquals(0, queueRepository.getHistory().size)
    }

    @Test
    fun dispatch_transientFailure500_whenRetriesExhausted_transitionsToPermanentFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        val item = createItem(attemptCount = 2, maxRetries = 3) // next attempt will be 3 == maxRetries
        queueRepository.enqueue(item)

        val result = dispatcher.dispatch(item, allowInsecureHttp = true)

        assertEquals(DeliveryStatus.FAILED_PERMANENT, result.status)
        assertEquals(503, result.responseCode)
        assertEquals(3, result.attemptCount)
        assertTrue(result.lastError?.contains("retries exhausted") == true)

        // Moved from pending to history
        assertEquals(0, queueRepository.getPendingItems().size)
        val history = queueRepository.getHistory()
        assertEquals(1, history.size)
        assertEquals(DeliveryStatus.FAILED_PERMANENT, history[0].status)
    }

    @Test
    fun dispatch_networkIOException_marksTransientFailure() = runBlocking {
        val deadUrl = "http://127.0.0.1:19998/webhook" // Unused port that will fail connection immediately
        val item = createItem(url = deadUrl, maxRetries = 3)
        queueRepository.enqueue(item)

        val result = dispatcher.dispatch(item, allowInsecureHttp = true)

        assertEquals(DeliveryStatus.FAILED_TRANSIENT, result.status)
        assertEquals(1, result.attemptCount)
        assertNull(result.responseCode)
        assertTrue(result.lastError?.contains("Network error") == true)

        val pending = queueRepository.getPendingItems()
        assertEquals(1, pending.size)
    }

    @Test
    fun processPendingQueue_offlineQueueProcessesAllItemsWhenAvailable() = runBlocking {
        val item1 = createItem(url = server.url("/webhook1").toString())
        val item2 = createItem(url = server.url("/webhook2").toString())
        queueRepository.enqueue(item1)
        queueRepository.enqueue(item2)

        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(204))

        val processedCount = dispatcher.processPendingQueue(allowInsecureHttp = true)
        assertEquals(2, processedCount)

        // All pending drained into history
        assertEquals(0, queueRepository.getPendingItems().size)
        val history = queueRepository.getHistory()
        assertEquals(2, history.size)
        assertTrue(history.all { it.status == DeliveryStatus.SUCCESS })
    }

    @Test
    fun calculateBackoffDelayMs_scalesExponentiallyAndCaps() {
        assertEquals(1000L, WebhookDispatcher.calculateBackoffDelayMs(0, 1000L, 60_000L))
        assertEquals(2000L, WebhookDispatcher.calculateBackoffDelayMs(1, 1000L, 60_000L))
        assertEquals(4000L, WebhookDispatcher.calculateBackoffDelayMs(2, 1000L, 60_000L))
        assertEquals(8000L, WebhookDispatcher.calculateBackoffDelayMs(3, 1000L, 60_000L))
        assertEquals(60_000L, WebhookDispatcher.calculateBackoffDelayMs(10, 1000L, 60_000L))
    }

    @Test
    fun sanitizeError_redactsSensitiveTokens() {
        val errorWithToken = "HTTP 401: Unauthorized for Bearer eyJhbGciOiJIUzI1NiJ9.test and password=SuperSecretPassword"
        val sanitized = WebhookDispatcher.sanitizeError(errorWithToken)

        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiJ9.test"))
        assertFalse(sanitized.contains("SuperSecretPassword"))
        assertTrue(sanitized.contains("Bearer ***REDACTED***"))
        assertTrue(sanitized.contains("password=***REDACTED***"))
    }

    @Test
    fun dispatch_malformedHeaderName_recordsPermanentFailureInsteadOfThrowing() = runBlocking {
        val item = createItem().copy(headers = mapOf("Bad Header" to "value"))
        queueRepository.enqueue(item)

        val result = dispatcher.dispatch(item, allowInsecureHttp = true)

        assertEquals(DeliveryStatus.FAILED_PERMANENT, result.status)
        assertNotNull(result.lastError)
        assertEquals(0, queueRepository.getPendingItems().size)
    }

    @Test
    fun testDelivery_malformedHeaderName_failsWithNonNullMessageInsteadOfThrowing() = runBlocking {
        val result = dispatcher.testDelivery(
            url = server.url("/webhook").toString(),
            method = "POST",
            headers = "Bad Header: value",
            payload = """{"event":"battery_low"}""",
            allowInsecureHttp = true
        )

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull()?.message)
    }
}
