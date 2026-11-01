package com.batmon.app.automation

/**
 * Pure Kotlin simulator modeling Home Assistant's smart-plug charging automation rules.
 *
 * Implements and verifies:
 * - Default Policy: Turn charger ON at or below 45%, Turn charger OFF at or above 80%
 * - Stale Data Protection: Rejects readings older than [maxStaleAgeMs] (default 30 min)
 * - Duplicate Command Suppression: Never commands ON if switch is already ON, or OFF if already OFF
 * - Switch Availability Guard: Never commands when switch is UNAVAILABLE or UNKNOWN
 * - Manual Override & Automation Toggle: Pauses commands when override active or automation disabled
 * - Safe Recovery: Correctly commands target state upon evaluation after HA or device restart
 */
class ChargingAutomationSimulator(
    val lowThreshold: Int = 45,
    val highThreshold: Int = 80,
    val maxStaleAgeMs: Long = 30 * 60 * 1000L // 30 minutes
) {

    enum class SwitchState {
        ON,
        OFF,
        UNAVAILABLE,
        UNKNOWN
    }

    enum class SwitchAction {
        TURN_ON,
        TURN_OFF,
        NO_OP
    }

    enum class DecisionReason {
        TURNED_ON_LOW_BATTERY,
        TURNED_OFF_HIGH_BATTERY,
        ALREADY_IN_TARGET_STATE,
        STALE_DATA_REJECTED,
        SWITCH_UNAVAILABLE,
        MANUAL_OVERRIDE_ACTIVE,
        AUTOMATION_DISABLED,
        DEVICE_UNAVAILABLE,
        WITHIN_NORMAL_RANGE
    }

    data class Decision(
        val action: SwitchAction,
        val reason: DecisionReason,
        val details: String
    )

    fun evaluate(
        batteryPercent: Int,
        batteryTimestampMs: Long,
        currentTimeMs: Long = System.currentTimeMillis(),
        isDeviceOnline: Boolean = true,
        currentSwitchState: SwitchState = SwitchState.OFF,
        automationEnabled: Boolean = true,
        manualOverrideActive: Boolean = false
    ): Decision {
        // 1. Guard: Automation master switch disabled
        if (!automationEnabled) {
            return Decision(
                action = SwitchAction.NO_OP,
                reason = DecisionReason.AUTOMATION_DISABLED,
                details = "Automation is disabled by user"
            )
        }

        // 2. Guard: Manual override active
        if (manualOverrideActive) {
            return Decision(
                action = SwitchAction.NO_OP,
                reason = DecisionReason.MANUAL_OVERRIDE_ACTIVE,
                details = "Manual override is active; automation paused"
            )
        }

        // 3. Guard: Switch unavailable or unknown
        if (currentSwitchState == SwitchState.UNAVAILABLE || currentSwitchState == SwitchState.UNKNOWN) {
            return Decision(
                action = SwitchAction.NO_OP,
                reason = DecisionReason.SWITCH_UNAVAILABLE,
                details = "Target smart plug switch is $currentSwitchState"
            )
        }

        // 4. Guard: Device is marked offline
        if (!isDeviceOnline) {
            return Decision(
                action = SwitchAction.NO_OP,
                reason = DecisionReason.DEVICE_UNAVAILABLE,
                details = "BatMon device availability is offline"
            )
        }

        // 5. Guard: STALE DATA PROTECTION (Critical safety check)
        // Never switch on a charger solely based on stale battery data.
        val ageMs = currentTimeMs - batteryTimestampMs
        if (ageMs > maxStaleAgeMs) {
            return Decision(
                action = SwitchAction.NO_OP,
                reason = DecisionReason.STALE_DATA_REJECTED,
                details = "Battery data is stale (age: ${ageMs / 1000}s > ${maxStaleAgeMs / 1000}s limit); unsafe for switching"
            )
        }

        // 6. Action: Low battery -> Turn ON
        if (batteryPercent <= lowThreshold) {
            return if (currentSwitchState == SwitchState.OFF) {
                Decision(
                    action = SwitchAction.TURN_ON,
                    reason = DecisionReason.TURNED_ON_LOW_BATTERY,
                    details = "Battery $batteryPercent% <= $lowThreshold% and switch is OFF -> Turning ON charger"
                )
            } else {
                // AVOID REPEATED COMMANDS
                Decision(
                    action = SwitchAction.NO_OP,
                    reason = DecisionReason.ALREADY_IN_TARGET_STATE,
                    details = "Battery $batteryPercent% <= $lowThreshold% but switch is already ON -> No command issued"
                )
            }
        }

        // 7. Action: High battery -> Turn OFF
        if (batteryPercent >= highThreshold) {
            return if (currentSwitchState == SwitchState.ON) {
                Decision(
                    action = SwitchAction.TURN_OFF,
                    reason = DecisionReason.TURNED_OFF_HIGH_BATTERY,
                    details = "Battery $batteryPercent% >= $highThreshold% and switch is ON -> Turning OFF charger"
                )
            } else {
                // AVOID REPEATED COMMANDS
                Decision(
                    action = SwitchAction.NO_OP,
                    reason = DecisionReason.ALREADY_IN_TARGET_STATE,
                    details = "Battery $batteryPercent% >= $highThreshold% but switch is already OFF -> No command issued"
                )
            }
        }

        // 8. Normal range: Between thresholds (e.g., 46% - 79%)
        return Decision(
            action = SwitchAction.NO_OP,
            reason = DecisionReason.WITHIN_NORMAL_RANGE,
            details = "Battery $batteryPercent% is between $lowThreshold% and $highThreshold% -> Maintaining current state ($currentSwitchState)"
        )
    }
}
