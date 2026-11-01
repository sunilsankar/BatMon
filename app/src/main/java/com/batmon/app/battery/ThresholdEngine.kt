package com.batmon.app.battery

enum class ThresholdAlert {
    LOW,
    HIGH
}

data class ThresholdState(
    val lowFired: Boolean = false,
    val highFired: Boolean = false
)

data class ThresholdEvaluationResult(
    val triggeredAlerts: List<ThresholdAlert>,
    val nextState: ThresholdState
)

object ThresholdEngine {

    fun isValidThresholds(low: Int, high: Int): Boolean {
        return low in 1..98 && high in 2..99 && low < high
    }

    /**
     * Evaluates battery percentage against low and high thresholds.
     * Suppresses duplicate alerts while remaining beyond a threshold.
     * Re-arms low alert once battery rises above low threshold.
     * Re-arms high alert once battery falls below high threshold.
     */
    fun evaluate(
        batteryPercent: Int,
        lowThreshold: Int,
        highThreshold: Int,
        currentState: ThresholdState
    ): ThresholdEvaluationResult {
        val alerts = mutableListOf<ThresholdAlert>()

        val nextLowFired = if (batteryPercent <= lowThreshold) {
            if (!currentState.lowFired) {
                alerts.add(ThresholdAlert.LOW)
            }
            true
        } else {
            // Re-arm when rising above low threshold
            false
        }

        val nextHighFired = if (batteryPercent >= highThreshold) {
            if (!currentState.highFired) {
                alerts.add(ThresholdAlert.HIGH)
            }
            true
        } else {
            // Re-arm when dropping below high threshold
            false
        }

        return ThresholdEvaluationResult(
            triggeredAlerts = alerts,
            nextState = ThresholdState(
                lowFired = nextLowFired,
                highFired = nextHighFired
            )
        )
    }
}
