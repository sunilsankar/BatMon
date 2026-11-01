package com.batmon.app.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.batmon.app.BatMonApplication
import com.batmon.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val app = context.applicationContext as? BatMonApplication
            val settingsRepo = app?.settingsRepository ?: SettingsRepository(context)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val settings = settingsRepo.getSettings()
                    BackgroundCoordinator.applyMonitoringMode(
                        context = context,
                        monitoringEnabled = settings.monitoringEnabled,
                        realTimeEnabled = settings.realTimeMonitoringEnabled
                    )
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
