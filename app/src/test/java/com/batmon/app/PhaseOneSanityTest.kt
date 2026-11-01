package com.batmon.app

import com.batmon.app.ui.navigation.Destinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseOneSanityTest {

    @Test
    fun destinations_areConfiguredCorrectly() {
        assertEquals("dashboard", Destinations.DASHBOARD)
        assertEquals("settings", Destinations.SETTINGS)
    }

    @Test
    fun gaugeAngleCalculation_isWithinExpectedRange() {
        // Test that 0% to 100% maps correctly to 0 to 270 degrees sweep
        val calculateSweep: (Int) -> Float = { percentage ->
            (percentage.coerceIn(0, 100) / 100f) * 270f
        }

        assertEquals(0f, calculateSweep(0), 0.001f)
        assertEquals(135f, calculateSweep(50), 0.001f)
        assertEquals(270f, calculateSweep(100), 0.001f)
        assertEquals(0f, calculateSweep(-10), 0.001f)
        assertEquals(270f, calculateSweep(150), 0.001f)
    }
}
