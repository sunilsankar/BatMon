package com.batmon.app

import android.app.Application
import com.batmon.app.background.BackgroundCoordinator
import com.batmon.app.battery.BatteryRepository
import com.batmon.app.data.SettingsRepository
import com.batmon.app.diagnostics.DiagnosticsRepository
import com.batmon.app.notifications.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BatMonApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var batteryRepository: BatteryRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var notificationService: NotificationService
        private set
    lateinit var diagnosticsRepository: DiagnosticsRepository
        private set
    lateinit var mqttPublisher: com.batmon.app.mqtt.MqttPublisher
        private set
    lateinit var webhookQueueRepository: com.batmon.app.webhooks.WebhookQueueRepository
        private set
    lateinit var webhookDispatcher: com.batmon.app.webhooks.WebhookDispatcher
        private set
    lateinit var updateManager: com.batmon.app.updater.UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        batteryRepository = BatteryRepository(this)
        settingsRepository = SettingsRepository(this)
        notificationService = NotificationService(this)
        diagnosticsRepository = DiagnosticsRepository(this)
        mqttPublisher = com.batmon.app.mqtt.MqttPublisher()
        webhookQueueRepository = com.batmon.app.webhooks.WebhookQueueRepository(this)
        webhookDispatcher = com.batmon.app.webhooks.WebhookDispatcher(webhookQueueRepository)
        updateManager = com.batmon.app.updater.UpdateManager()

        applicationScope.launch {
            settingsRepository.ensureInitialized()
            val initialSettings = settingsRepository.getSettings()
            BackgroundCoordinator.applyMonitoringMode(
                context = this@BatMonApplication,
                monitoringEnabled = initialSettings.monitoringEnabled,
                realTimeEnabled = initialSettings.realTimeMonitoringEnabled
            )
        }
    }
}
