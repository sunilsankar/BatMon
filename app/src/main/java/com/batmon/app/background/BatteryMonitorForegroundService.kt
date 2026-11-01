package com.batmon.app.background

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.batmon.app.BatMonApplication
import com.batmon.app.battery.BatteryRepository
import com.batmon.app.battery.ThresholdAlert
import com.batmon.app.battery.ThresholdEngine
import com.batmon.app.data.SettingsRepository
import com.batmon.app.notifications.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BatteryMonitorForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as? BatMonApplication
        val batteryRepo = app?.batteryRepository ?: BatteryRepository(this)
        val settingsRepo = app?.settingsRepository ?: SettingsRepository(this)
        val notificationService = app?.notificationService ?: NotificationService(this)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val initialBattery = batteryRepo.getCurrentState()
        val initialNotification = notificationService.buildForegroundNotification(
            percent = initialBattery.percentage,
            chargingState = initialBattery.chargingStatus
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationService.NOTIFICATION_ID_FOREGROUND,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationService.NOTIFICATION_ID_FOREGROUND, initialNotification)
        }

        serviceScope.launch {
            combine(batteryRepo.batteryState, settingsRepo.settings) { battery, settings ->
                Pair(battery, settings)
            }.collect { (battery, settings) ->
                // Update ongoing persistent notification
                val updatedNotification = notificationService.buildForegroundNotification(
                    percent = battery.percentage,
                    chargingState = battery.chargingStatus
                )
                notificationManager.notify(
                    NotificationService.NOTIFICATION_ID_FOREGROUND,
                    updatedNotification
                )

                if (!settings.monitoringEnabled) return@collect

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

                settingsRepo.recordSyncTimestamp(battery.timestampMs)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, BatteryMonitorForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BatteryMonitorForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
