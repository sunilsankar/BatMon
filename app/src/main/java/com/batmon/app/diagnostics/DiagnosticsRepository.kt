package com.batmon.app.diagnostics

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.batmon.app.battery.StaleDataDetector
import com.batmon.app.battery.StalenessResult
import com.batmon.app.data.BatMonSettings

data class DiagnosticsInfo(
    val deviceId: String,
    val deviceName: String,
    val androidRelease: String,
    val apiLevel: Int,
    val monitoringMode: String,
    val isPowerSaveMode: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
    val stalenessResult: StalenessResult,
    val limitations: List<String>
)

class DiagnosticsRepository(private val context: Context) {

    fun getDiagnostics(settings: BatMonSettings, lastReadingTimestampMs: Long): DiagnosticsInfo {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = powerManager?.isPowerSaveMode ?: false
        val isIgnoringOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }

        val mode = when {
            !settings.monitoringEnabled -> "Disabled"
            settings.realTimeMonitoringEnabled -> "Real-Time (Foreground Service)"
            else -> "Low-Power (WorkManager periodic)"
        }

        val staleness = StaleDataDetector.evaluate(
            timestampMs = lastReadingTimestampMs,
            isRealTimeMode = settings.realTimeMonitoringEnabled
        )

        val limitations = listOf(
            "Low-power WorkManager periodic sync runs at minimum 15-minute intervals and may be deferred during Doze.",
            "Real-time monitoring requires an ongoing foreground service notification.",
            "OEM battery-savers (Samsung, Xiaomi, Huawei) may suspend background tasks unless battery optimization is disabled.",
            "Force-stopping the app stops all background workers and broadcast receivers until manual launch.",
            "Stale sensor readings are treated as unsafe for smart-plug automated switching."
        )

        return DiagnosticsInfo(
            deviceId = settings.deviceId,
            deviceName = settings.deviceName,
            androidRelease = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            monitoringMode = mode,
            isPowerSaveMode = isPowerSave,
            isIgnoringBatteryOptimizations = isIgnoringOptimizations,
            stalenessResult = staleness,
            limitations = limitations
        )
    }
}
