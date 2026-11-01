package com.batmon.app.diagnostics

import com.batmon.app.battery.StaleDataDetector
import com.batmon.app.battery.StalenessState
import com.batmon.app.data.BatMonSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class DiagnosticsTest {

    @Test
    fun testMonitoringModeDescriptions() {
        val disabledSettings = BatMonSettings(monitoringEnabled = false)
        val modeDisabled = when {
            !disabledSettings.monitoringEnabled -> "Disabled"
            disabledSettings.realTimeMonitoringEnabled -> "Real-Time (Foreground Service)"
            else -> "Low-Power (WorkManager periodic)"
        }
        assertEquals("Disabled", modeDisabled)

        val lowPowerSettings = BatMonSettings(monitoringEnabled = true, realTimeMonitoringEnabled = false)
        val modeLowPower = when {
            !lowPowerSettings.monitoringEnabled -> "Disabled"
            lowPowerSettings.realTimeMonitoringEnabled -> "Real-Time (Foreground Service)"
            else -> "Low-Power (WorkManager periodic)"
        }
        assertEquals("Low-Power (WorkManager periodic)", modeLowPower)

        val realTimeSettings = BatMonSettings(monitoringEnabled = true, realTimeMonitoringEnabled = true)
        val modeRealTime = when {
            !realTimeSettings.monitoringEnabled -> "Disabled"
            realTimeSettings.realTimeMonitoringEnabled -> "Real-Time (Foreground Service)"
            else -> "Low-Power (WorkManager periodic)"
        }
        assertEquals("Real-Time (Foreground Service)", modeRealTime)
    }

    @Test
    fun testDiagnosticsLimitationsContent() {
        val limitations = listOf(
            "Low-power WorkManager periodic sync runs at minimum 15-minute intervals and may be deferred during Doze.",
            "Real-time monitoring requires an ongoing foreground service notification.",
            "OEM battery-savers (Samsung, Xiaomi, Huawei) may suspend background tasks unless battery optimization is disabled.",
            "Force-stopping the app stops all background workers and broadcast receivers until manual launch.",
            "Stale sensor readings are treated as unsafe for smart-plug automated switching."
        )

        assertEquals(5, limitations.size)
        assertTrue(limitations.any { it.contains("15-minute intervals") })
        assertTrue(limitations.any { it.contains("Doze") })
        assertTrue(limitations.any { it.contains("Force-stopping") })
        assertTrue(limitations.any { it.contains("unsafe") })
    }

    @Test
    fun testStalenessWarningInDiagnostics() {
        val now = 2_000_000_000L
        val staleReading = now - TimeUnit.MINUTES.toMillis(40)

        val staleness = StaleDataDetector.evaluate(
            timestampMs = staleReading,
            currentTimeMs = now,
            isRealTimeMode = false
        )

        assertEquals(StalenessState.STALE, staleness.state)
        assertTrue(staleness.isUnsafeForAutomatedSwitching)
        assertTrue(staleness.userSummary.contains("unsafe"))
    }
}
