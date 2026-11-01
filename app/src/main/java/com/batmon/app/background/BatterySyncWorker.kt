package com.batmon.app.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.batmon.app.BatMonApplication
import com.batmon.app.battery.BatteryRepository
import com.batmon.app.battery.ThresholdAlert
import com.batmon.app.battery.ThresholdEngine
import com.batmon.app.data.SettingsRepository
import com.batmon.app.notifications.NotificationService
import java.util.concurrent.TimeUnit

class BatterySyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? BatMonApplication
        val batteryRepo = app?.batteryRepository ?: BatteryRepository(applicationContext)
        val settingsRepo = app?.settingsRepository ?: SettingsRepository(applicationContext)
        val notificationService = app?.notificationService ?: NotificationService(applicationContext)

        val settings = settingsRepo.getSettings()
        if (!settings.monitoringEnabled) {
            return Result.success()
        }

        val battery = batteryRepo.getCurrentState()
        val result = ThresholdEngine.evaluate(
            batteryPercent = battery.percentage,
            lowThreshold = settings.lowThreshold,
            highThreshold = settings.highThreshold,
            currentState = settings.thresholdState
        )

        if (result.nextState != settings.thresholdState) {
            settingsRepo.updateThresholdState(result.nextState)
        }

        if (settings.notificationsEnabled) {
            for (alert in result.triggeredAlerts) {
                when (alert) {
                    ThresholdAlert.LOW -> notificationService.showLowBatteryNotification(
                        percent = battery.percentage,
                        threshold = settings.lowThreshold,
                        chargingState = battery.chargingStatus
                    )
                    ThresholdAlert.HIGH -> notificationService.showHighBatteryNotification(
                        percent = battery.percentage,
                        threshold = settings.highThreshold,
                        chargingState = battery.chargingStatus
                    )
                }
            }
        }

        // Webhooks on threshold alert
        if (settings.webhooks.enabled) {
            for (alert in result.triggeredAlerts) {
                when (alert) {
                    ThresholdAlert.LOW -> app?.webhookDispatcher?.enqueueAndProcess(
                        event = com.batmon.app.webhooks.WebhookEvent.BATTERY_LOW,
                        battery = battery,
                        settings = settings,
                        thresholdPercent = settings.lowThreshold
                    )
                    ThresholdAlert.HIGH -> app?.webhookDispatcher?.enqueueAndProcess(
                        event = com.batmon.app.webhooks.WebhookEvent.BATTERY_HIGH,
                        battery = battery,
                        settings = settings,
                        thresholdPercent = settings.highThreshold
                    )
                }
            }
            // Also process any retryable items in queue
            app?.webhookDispatcher?.processPendingQueue(settings.webhooks.allowInsecureHttp)
        }

        // Publish to MQTT if configured
        if (settings.mqtt.enabled && settings.mqtt.host.isNotBlank()) {
            for (alert in result.triggeredAlerts) {
                when (alert) {
                    ThresholdAlert.LOW -> app?.mqttPublisher?.publishEvent(
                        "battery_low", battery, settings, settings.lowThreshold
                    )
                    ThresholdAlert.HIGH -> app?.mqttPublisher?.publishEvent(
                        "battery_high", battery, settings, settings.highThreshold
                    )
                }
            }
            app?.mqttPublisher?.publishState(battery, settings)
        }

        settingsRepo.recordSyncTimestamp(System.currentTimeMillis())
        return Result.success()
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "batmon_periodic_battery_sync"

        fun schedule(context: Context) {
            // Periodic work minimum interval is 15 minutes by Android system constraint
            val periodicWork = PeriodicWorkRequestBuilder<BatterySyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.NONE)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
        }
    }
}
