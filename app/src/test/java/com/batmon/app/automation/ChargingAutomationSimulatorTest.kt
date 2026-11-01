package com.batmon.app.automation

import com.batmon.app.automation.ChargingAutomationSimulator.DecisionReason
import com.batmon.app.automation.ChargingAutomationSimulator.SwitchAction
import com.batmon.app.automation.ChargingAutomationSimulator.SwitchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingAutomationSimulatorTest {

    private val simulator = ChargingAutomationSimulator(
        lowThreshold = 45,
        highThreshold = 80,
        maxStaleAgeMs = 30 * 60 * 1000L // 30 minutes
    )

    private val now = 1000000000L

    @Test
    fun testLowThreshold_turnsOnCharger_whenSwitchIsOff() {
        // Battery reaches 45% -> should turn ON charger
        val decision = simulator.evaluate(
            batteryPercent = 45,
            batteryTimestampMs = now,
            currentTimeMs = now,
            currentSwitchState = SwitchState.OFF
        )

        assertEquals(SwitchAction.TURN_ON, decision.action)
        assertEquals(DecisionReason.TURNED_ON_LOW_BATTERY, decision.reason)
    }

    @Test
    fun testLowThreshold_avoidsRepeatedCommands_whenSwitchIsAlreadyOn() {
        // Battery is at 44% or 40%, but charger switch is already ON -> no repeated command
        val decision = simulator.evaluate(
            batteryPercent = 44,
            batteryTimestampMs = now,
            currentTimeMs = now,
            currentSwitchState = SwitchState.ON
        )

        assertEquals(SwitchAction.NO_OP, decision.action)
        assertEquals(DecisionReason.ALREADY_IN_TARGET_STATE, decision.reason)
    }

    @Test
    fun testHighThreshold_turnsOffCharger_whenSwitchIsOn() {
        // Battery reaches 80% -> should turn OFF charger
        val decision = simulator.evaluate(
            batteryPercent = 80,
            batteryTimestampMs = now,
            currentTimeMs = now,
            currentSwitchState = SwitchState.ON
        )

        assertEquals(SwitchAction.TURN_OFF, decision.action)
        assertEquals(DecisionReason.TURNED_OFF_HIGH_BATTERY, decision.reason)
    }

    @Test
    fun testHighThreshold_avoidsRepeatedCommands_whenSwitchIsAlreadyOff() {
        // Battery reaches 85%, but charger is already OFF -> no repeated command
        val decision = simulator.evaluate(
            batteryPercent = 85,
            batteryTimestampMs = now,
            currentTimeMs = now,
            currentSwitchState = SwitchState.OFF
        )

        assertEquals(SwitchAction.NO_OP, decision.action)
        assertEquals(DecisionReason.ALREADY_IN_TARGET_STATE, decision.reason)
    }

    @Test
    fun testStaleData_isRejected_neverTurnsOnCharger() {
        // Battery is critically low (20%), but timestamp is 31 minutes old (> 30 min limit)
        val staleTimestamp = now - (31 * 60 * 1000L)
        val decision = simulator.evaluate(
            batteryPercent = 20,
            batteryTimestampMs = staleTimestamp,
            currentTimeMs = now,
            currentSwitchState = SwitchState.OFF
        )

        assertEquals(SwitchAction.NO_OP, decision.action)
        assertEquals(DecisionReason.STALE_DATA_REJECTED, decision.reason)
        assertTrue(decision.details.contains("stale"))
    }

    @Test
    fun testFreshData_justWithinLimit_isAccepted() {
        // Battery is 45%, timestamp is 29 minutes old (< 30 min limit) -> accepted
        val freshTimestamp = now - (29 * 60 * 1000L)
        val decision = simulator.evaluate(
            batteryPercent = 45,
            batteryTimestampMs = freshTimestamp,
            currentTimeMs = now,
            currentSwitchState = SwitchState.OFF
        )

        assertEquals(SwitchAction.TURN_ON, decision.action)
        assertEquals(DecisionReason.TURNED_ON_LOW_BATTERY, decision.reason)
    }

    @Test
    fun testUnavailableSwitch_suppressesCommands() {
        // Switch is offline/unavailable -> do not issue commands
        val decision = simulator.evaluate(
            batteryPercent = 30,
            batteryTimestampMs = now,
            currentTimeMs = now,
            currentSwitchState = SwitchState.UNAVAILABLE
        )

        assertEquals(SwitchAction.NO_OP, decision.action)
        assertEquals(DecisionReason.SWITCH_UNAVAILABLE, decision.reason)
    }

    @Test
    fun testManualOverride_pausesAutomation() {
        // User manually turned on manual override -> do not automatically turn off or on
        val decision = simulator.evaluate(
            batteryPercent = 95,
            batteryTimestampMs = now,
            currentTimeMs = now,
            currentSwitchState = SwitchState.ON,
            manualOverrideActive = true
        )

        assertEquals(SwitchAction.NO_OP, decision.action)
        assertEquals(DecisionReason.MANUAL_OVERRIDE_ACTIVE, decision.reason)
    }

    @Test
    fun testAutomationDisabled_suppressesAllActions() {
        val decision = simulator.evaluate(
            batteryPercent = 10,
            batteryTimestampMs = now,
            currentTimeMs = now,
            currentSwitchState = SwitchState.OFF,
            automationEnabled = false
        )

        assertEquals(SwitchAction.NO_OP, decision.action)
        assertEquals(DecisionReason.AUTOMATION_DISABLED, decision.reason)
    }

    @Test
    fun testDeviceOffline_suppressesActions() {
        val decision = simulator.evaluate(
            batteryPercent = 40,
            batteryTimestampMs = now,
            currentTimeMs = now,
            isDeviceOnline = false,
            currentSwitchState = SwitchState.OFF
        )

        assertEquals(SwitchAction.NO_OP, decision.action)
        assertEquals(DecisionReason.DEVICE_UNAVAILABLE, decision.reason)
    }

    @Test
    fun testHomeAssistantRestartRecovery_safeEvaluation() {
        // Scenario 1: HA restarts after power outage, battery is 35% and fresh, switch was OFF
        val restartDecisionLow = simulator.evaluate(
            batteryPercent = 35,
            batteryTimestampMs = now,
            currentTimeMs = now,
            currentSwitchState = SwitchState.OFF
        )
        assertEquals(SwitchAction.TURN_ON, restartDecisionLow.action)
        assertEquals(DecisionReason.TURNED_ON_LOW_BATTERY, restartDecisionLow.reason)

        // Scenario 2: HA restarts, battery is 88%, switch was ON
        val restartDecisionHigh = simulator.evaluate(
            batteryPercent = 88,
            batteryTimestampMs = now,
            currentTimeMs = now,
            currentSwitchState = SwitchState.ON
        )
        assertEquals(SwitchAction.TURN_OFF, restartDecisionHigh.action)
        assertEquals(DecisionReason.TURNED_OFF_HIGH_BATTERY, restartDecisionHigh.reason)
    }

    @Test
    fun testBetweenThresholds_maintainsCurrentState() {
        // Battery is 60%
        val decision = simulator.evaluate(
            batteryPercent = 60,
            batteryTimestampMs = now,
            currentTimeMs = now,
            currentSwitchState = SwitchState.OFF
        )
        assertEquals(SwitchAction.NO_OP, decision.action)
        assertEquals(DecisionReason.WITHIN_NORMAL_RANGE, decision.reason)
    }

    @Test
    fun testFullSimulationLifecycle_chargeAndDischarge() {
        var switch = SwitchState.OFF

        // 1. Starting at 70% (discharging)
        var dec = simulator.evaluate(70, now, now, currentSwitchState = switch)
        assertEquals(SwitchAction.NO_OP, dec.action)

        // 2. Battery drops to 46% (above threshold)
        dec = simulator.evaluate(46, now, now, currentSwitchState = switch)
        assertEquals(SwitchAction.NO_OP, dec.action)

        // 3. Battery drops to 45% (hits low threshold) -> Turn ON
        dec = simulator.evaluate(45, now, now, currentSwitchState = switch)
        assertEquals(SwitchAction.TURN_ON, dec.action)
        switch = SwitchState.ON // Switch flips ON

        // 4. Battery drops further to 43% while charger initiates -> Already ON, NO_OP
        dec = simulator.evaluate(43, now, now, currentSwitchState = switch)
        assertEquals(SwitchAction.NO_OP, dec.action)
        assertEquals(DecisionReason.ALREADY_IN_TARGET_STATE, dec.reason)

        // 5. Battery charging up: 50%, 65%, 75% -> maintaining ON
        dec = simulator.evaluate(65, now, now, currentSwitchState = switch)
        assertEquals(SwitchAction.NO_OP, dec.action)

        // 6. Battery reaches 80% (hits high threshold) -> Turn OFF
        dec = simulator.evaluate(80, now, now, currentSwitchState = switch)
        assertEquals(SwitchAction.TURN_OFF, dec.action)
        switch = SwitchState.OFF // Switch flips OFF

        // 7. Battery continues to 81% -> Already OFF, NO_OP
        dec = simulator.evaluate(81, now, now, currentSwitchState = switch)
        assertEquals(SwitchAction.NO_OP, dec.action)
        assertEquals(DecisionReason.ALREADY_IN_TARGET_STATE, dec.reason)
    }
}
