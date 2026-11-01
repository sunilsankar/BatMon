package com.batmon.app.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdEngineTest {

    @Test
    fun `low threshold crossing triggers low alert`() {
        val initial = ThresholdState(lowFired = false, highFired = false)
        val result = ThresholdEngine.evaluate(
            batteryPercent = 45,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = initial
        )

        assertEquals(listOf(ThresholdAlert.LOW), result.triggeredAlerts)
        assertTrue(result.nextState.lowFired)
        assertFalse(result.nextState.highFired)
    }

    @Test
    fun `battery below low threshold triggers low alert`() {
        val initial = ThresholdState(lowFired = false, highFired = false)
        val result = ThresholdEngine.evaluate(
            batteryPercent = 30,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = initial
        )

        assertEquals(listOf(ThresholdAlert.LOW), result.triggeredAlerts)
        assertTrue(result.nextState.lowFired)
    }

    @Test
    fun `low threshold duplicate suppression while remaining low`() {
        val state = ThresholdState(lowFired = true, highFired = false)

        // Still at 40%
        val result1 = ThresholdEngine.evaluate(
            batteryPercent = 40,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = state
        )
        assertTrue("No alert when already fired", result1.triggeredAlerts.isEmpty())
        assertTrue(result1.nextState.lowFired)

        // Drops further to 35%
        val result2 = ThresholdEngine.evaluate(
            batteryPercent = 35,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = result1.nextState
        )
        assertTrue("No duplicate alert when dropping further", result2.triggeredAlerts.isEmpty())
        assertTrue(result2.nextState.lowFired)
    }

    @Test
    fun `low threshold re-arms after battery rises above threshold`() {
        val state = ThresholdState(lowFired = true, highFired = false)

        // Rises to 46% (above 45%)
        val recovery = ThresholdEngine.evaluate(
            batteryPercent = 46,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = state
        )
        assertTrue(recovery.triggeredAlerts.isEmpty())
        assertFalse("Low alert must re-arm", recovery.nextState.lowFired)

        // Drops back to 45% -> should fire again!
        val fireAgain = ThresholdEngine.evaluate(
            batteryPercent = 45,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = recovery.nextState
        )
        assertEquals(listOf(ThresholdAlert.LOW), fireAgain.triggeredAlerts)
        assertTrue(fireAgain.nextState.lowFired)
    }

    @Test
    fun `high threshold crossing triggers high alert`() {
        val initial = ThresholdState(lowFired = false, highFired = false)
        val result = ThresholdEngine.evaluate(
            batteryPercent = 80,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = initial
        )

        assertEquals(listOf(ThresholdAlert.HIGH), result.triggeredAlerts)
        assertTrue(result.nextState.highFired)
        assertFalse(result.nextState.lowFired)
    }

    @Test
    fun `battery above high threshold triggers high alert`() {
        val initial = ThresholdState(lowFired = false, highFired = false)
        val result = ThresholdEngine.evaluate(
            batteryPercent = 95,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = initial
        )

        assertEquals(listOf(ThresholdAlert.HIGH), result.triggeredAlerts)
        assertTrue(result.nextState.highFired)
    }

    @Test
    fun `high threshold duplicate suppression while remaining high`() {
        val state = ThresholdState(lowFired = false, highFired = true)

        val result1 = ThresholdEngine.evaluate(
            batteryPercent = 85,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = state
        )
        assertTrue("No alert when already fired", result1.triggeredAlerts.isEmpty())
        assertTrue(result1.nextState.highFired)

        val result2 = ThresholdEngine.evaluate(
            batteryPercent = 90,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = result1.nextState
        )
        assertTrue("No duplicate alert when charging further", result2.triggeredAlerts.isEmpty())
        assertTrue(result2.nextState.highFired)
    }

    @Test
    fun `high threshold re-arms after battery drops below threshold`() {
        val state = ThresholdState(lowFired = false, highFired = true)

        // Drops to 79% (below 80%)
        val recovery = ThresholdEngine.evaluate(
            batteryPercent = 79,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = state
        )
        assertTrue(recovery.triggeredAlerts.isEmpty())
        assertFalse("High alert must re-arm", recovery.nextState.highFired)

        // Rises back to 80% -> should fire again!
        val fireAgain = ThresholdEngine.evaluate(
            batteryPercent = 80,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = recovery.nextState
        )
        assertEquals(listOf(ThresholdAlert.HIGH), fireAgain.triggeredAlerts)
        assertTrue(fireAgain.nextState.highFired)
    }

    @Test
    fun `startup evaluation on fresh install below low threshold`() {
        // Fresh install with default state
        val freshState = ThresholdState(lowFired = false, highFired = false)
        val result = ThresholdEngine.evaluate(
            batteryPercent = 30,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = freshState
        )

        assertEquals(listOf(ThresholdAlert.LOW), result.triggeredAlerts)
        assertTrue(result.nextState.lowFired)
    }

    @Test
    fun `startup evaluation after restart suppresses already fired low event`() {
        // Restored from DataStore where low was already processed
        val restoredState = ThresholdState(lowFired = true, highFired = false)
        val result = ThresholdEngine.evaluate(
            batteryPercent = 30,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = restoredState
        )

        assertTrue("Must not duplicate event on restart", result.triggeredAlerts.isEmpty())
        assertTrue(result.nextState.lowFired)
    }

    @Test
    fun `startup evaluation after restart re-arms if battery recovered during restart`() {
        // Persisted state was lowFired=true, but device charged while off
        val restoredState = ThresholdState(lowFired = true, highFired = false)
        val result = ThresholdEngine.evaluate(
            batteryPercent = 65,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = restoredState
        )

        assertTrue(result.triggeredAlerts.isEmpty())
        assertFalse("State should be re-armed after recovery", result.nextState.lowFired)
    }

    @Test
    fun `normal battery between thresholds produces no alerts`() {
        val initial = ThresholdState(lowFired = false, highFired = false)
        val result = ThresholdEngine.evaluate(
            batteryPercent = 60,
            lowThreshold = 45,
            highThreshold = 80,
            currentState = initial
        )

        assertTrue(result.triggeredAlerts.isEmpty())
        assertFalse(result.nextState.lowFired)
        assertFalse(result.nextState.highFired)
    }

    @Test
    fun `threshold validation rules`() {
        assertTrue(ThresholdEngine.isValidThresholds(45, 80))
        assertTrue(ThresholdEngine.isValidThresholds(1, 99))
        assertTrue(ThresholdEngine.isValidThresholds(10, 20))

        assertFalse("Low must be < high", ThresholdEngine.isValidThresholds(80, 45))
        assertFalse("Low cannot equal high", ThresholdEngine.isValidThresholds(50, 50))
        assertFalse("Low cannot be 0", ThresholdEngine.isValidThresholds(0, 80))
        assertFalse("High cannot exceed 99", ThresholdEngine.isValidThresholds(45, 100))
    }

    @Test
    fun `simulated complete charge-discharge cycle`() {
        var state = ThresholdState()

        // 1. Initial 60% -> no alerts
        var res = ThresholdEngine.evaluate(60, 45, 80, state)
        assertTrue(res.triggeredAlerts.isEmpty())
        state = res.nextState

        // 2. Discharging to 45% -> LOW alert
        res = ThresholdEngine.evaluate(45, 45, 80, state)
        assertEquals(listOf(ThresholdAlert.LOW), res.triggeredAlerts)
        state = res.nextState

        // 3. Discharging further to 30% -> suppressed
        res = ThresholdEngine.evaluate(30, 45, 80, state)
        assertTrue(res.triggeredAlerts.isEmpty())
        state = res.nextState

        // 4. Charging starts, rises to 44% -> still suppressed
        res = ThresholdEngine.evaluate(44, 45, 80, state)
        assertTrue(res.triggeredAlerts.isEmpty())
        state = res.nextState

        // 5. Charges to 46% -> re-arms low alert
        res = ThresholdEngine.evaluate(46, 45, 80, state)
        assertTrue(res.triggeredAlerts.isEmpty())
        assertFalse(res.nextState.lowFired)
        state = res.nextState

        // 6. Charges to 79% -> no alerts
        res = ThresholdEngine.evaluate(79, 45, 80, state)
        assertTrue(res.triggeredAlerts.isEmpty())
        state = res.nextState

        // 7. Charges to 80% -> HIGH alert
        res = ThresholdEngine.evaluate(80, 45, 80, state)
        assertEquals(listOf(ThresholdAlert.HIGH), res.triggeredAlerts)
        state = res.nextState

        // 8. Charges to 85% -> suppressed
        res = ThresholdEngine.evaluate(85, 45, 80, state)
        assertTrue(res.triggeredAlerts.isEmpty())
        state = res.nextState

        // 9. Unplugged, drops to 79% -> re-arms high alert
        res = ThresholdEngine.evaluate(79, 45, 80, state)
        assertTrue(res.triggeredAlerts.isEmpty())
        assertFalse(res.nextState.highFired)
        state = res.nextState

        // 10. Drops to 60% -> normal state
        res = ThresholdEngine.evaluate(60, 45, 80, state)
        assertTrue(res.triggeredAlerts.isEmpty())
        assertFalse(res.nextState.lowFired)
        assertFalse(res.nextState.highFired)
    }

    @Test
    fun `independent low and high threshold range validation`() {
        // Valid independent combinations
        assertTrue(ThresholdEngine.isValidThresholds(1, 99))
        assertTrue(ThresholdEngine.isValidThresholds(20, 80))
        assertTrue(ThresholdEngine.isValidThresholds(45, 46))
        assertTrue(ThresholdEngine.isValidThresholds(98, 99))

        // Invalid: low >= high
        assertFalse(ThresholdEngine.isValidThresholds(50, 50))
        assertFalse(ThresholdEngine.isValidThresholds(80, 45))
        assertFalse(ThresholdEngine.isValidThresholds(99, 98))

        // Out of bounds: low < 1 or high > 99
        assertFalse(ThresholdEngine.isValidThresholds(0, 80))
        assertFalse(ThresholdEngine.isValidThresholds(45, 100))
    }
}
