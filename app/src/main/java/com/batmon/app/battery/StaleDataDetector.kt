package com.batmon.app.battery

import java.util.concurrent.TimeUnit

enum class StalenessState {
    FRESH,
    DELAYED,
    STALE
}

data class StalenessResult(
    val state: StalenessState,
    val ageMs: Long,
    val isUnsafeForAutomatedSwitching: Boolean,
    val userSummary: String
)

object StaleDataDetector {

    val DEFAULT_LOW_POWER_STALE_TIMEOUT_MS: Long = TimeUnit.MINUTES.toMillis(30)
    val DEFAULT_REALTIME_STALE_TIMEOUT_MS: Long = TimeUnit.MINUTES.toMillis(3)
    val DEFAULT_DELAYED_WARNING_MS: Long = TimeUnit.MINUTES.toMillis(15)

    fun evaluate(
        timestampMs: Long,
        currentTimeMs: Long = System.currentTimeMillis(),
        isRealTimeMode: Boolean = false,
        staleTimeoutMs: Long = if (isRealTimeMode) DEFAULT_REALTIME_STALE_TIMEOUT_MS else DEFAULT_LOW_POWER_STALE_TIMEOUT_MS
    ): StalenessResult {
        if (timestampMs <= 0L) {
            return StalenessResult(
                state = StalenessState.STALE,
                ageMs = Long.MAX_VALUE,
                isUnsafeForAutomatedSwitching = true,
                userSummary = "No readings received yet (stale)"
            )
        }

        val ageMs = (currentTimeMs - timestampMs).coerceAtLeast(0L)
        val isStale = ageMs >= staleTimeoutMs
        val isDelayed = !isStale && ageMs >= (staleTimeoutMs / 2).coerceAtLeast(DEFAULT_DELAYED_WARNING_MS.coerceAtMost(staleTimeoutMs))

        val state = when {
            isStale -> StalenessState.STALE
            isDelayed -> StalenessState.DELAYED
            else -> StalenessState.FRESH
        }

        val ageMinutes = TimeUnit.MILLISECONDS.toMinutes(ageMs)
        val userSummary = when (state) {
            StalenessState.FRESH -> "Fresh (${ageMinutes}m old)"
            StalenessState.DELAYED -> "Delayed (${ageMinutes}m old - background sync pending)"
            StalenessState.STALE -> "Stale (${ageMinutes}m old - unsafe for charging automation)"
        }

        return StalenessResult(
            state = state,
            ageMs = ageMs,
            isUnsafeForAutomatedSwitching = isStale,
            userSummary = userSummary
        )
    }
}
