package com.batmon.app.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class StaleDataDetectorTest {

    private val baseTime = 1_000_000_000L

    @Test
    fun testZeroOrNegativeTimestampIsStale() {
        val result = StaleDataDetector.evaluate(
            timestampMs = 0L,
            currentTimeMs = baseTime
        )
        assertEquals(StalenessState.STALE, result.state)
        assertTrue(result.isUnsafeForAutomatedSwitching)
    }

    @Test
    fun testFreshReadingInLowPowerMode() {
        val readingTime = baseTime - TimeUnit.MINUTES.toMillis(5)
        val result = StaleDataDetector.evaluate(
            timestampMs = readingTime,
            currentTimeMs = baseTime,
            isRealTimeMode = false
        )
        assertEquals(StalenessState.FRESH, result.state)
        assertFalse(result.isUnsafeForAutomatedSwitching)
    }

    @Test
    fun testDelayedReadingInLowPowerMode() {
        val readingTime = baseTime - TimeUnit.MINUTES.toMillis(18)
        val result = StaleDataDetector.evaluate(
            timestampMs = readingTime,
            currentTimeMs = baseTime,
            isRealTimeMode = false
        )
        assertEquals(StalenessState.DELAYED, result.state)
        assertFalse(result.isUnsafeForAutomatedSwitching)
    }

    @Test
    fun testStaleReadingInLowPowerMode() {
        val readingTime = baseTime - TimeUnit.MINUTES.toMillis(35)
        val result = StaleDataDetector.evaluate(
            timestampMs = readingTime,
            currentTimeMs = baseTime,
            isRealTimeMode = false
        )
        assertEquals(StalenessState.STALE, result.state)
        assertTrue(result.isUnsafeForAutomatedSwitching)
    }

    @Test
    fun testRealTimeModeStalenessThreshold() {
        // 1 minute old is fresh in real-time mode
        val freshResult = StaleDataDetector.evaluate(
            timestampMs = baseTime - TimeUnit.MINUTES.toMillis(1),
            currentTimeMs = baseTime,
            isRealTimeMode = true
        )
        assertEquals(StalenessState.FRESH, freshResult.state)
        assertFalse(freshResult.isUnsafeForAutomatedSwitching)

        // 4 minutes old is stale in real-time mode (timeout is 3 mins)
        val staleResult = StaleDataDetector.evaluate(
            timestampMs = baseTime - TimeUnit.MINUTES.toMillis(4),
            currentTimeMs = baseTime,
            isRealTimeMode = true
        )
        assertEquals(StalenessState.STALE, staleResult.state)
        assertTrue(staleResult.isUnsafeForAutomatedSwitching)
    }
}
