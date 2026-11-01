package com.batmon.app.background

import android.content.Context

object BackgroundCoordinator {

    fun applyMonitoringMode(
        context: Context,
        monitoringEnabled: Boolean,
        realTimeEnabled: Boolean
    ) {
        if (!monitoringEnabled) {
            BatterySyncWorker.cancel(context)
            BatteryMonitorForegroundService.stop(context)
            return
        }

        if (realTimeEnabled) {
            // Real-time mode uses persistent foreground service
            BatterySyncWorker.cancel(context)
            BatteryMonitorForegroundService.start(context)
        } else {
            // Low-power mode uses periodic WorkManager
            BatteryMonitorForegroundService.stop(context)
            BatterySyncWorker.schedule(context)
        }
    }
}
