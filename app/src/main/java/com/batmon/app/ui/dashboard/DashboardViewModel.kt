package com.batmon.app.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batmon.app.BatMonApplication
import com.batmon.app.background.BackgroundCoordinator
import com.batmon.app.battery.BatteryState
import com.batmon.app.battery.StaleDataDetector
import com.batmon.app.battery.StalenessResult
import com.batmon.app.battery.StalenessState
import com.batmon.app.battery.ThresholdAlert
import com.batmon.app.battery.ThresholdEngine
import com.batmon.app.data.BatMonSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DashboardUiState(
    val batteryPercent: Int = 0,
    val chargingState: String = "Unknown",
    val lowThreshold: Int = 45,
    val highThreshold: Int = 80,
    val monitoringActive: Boolean = true,
    val monitoringModeTitle: String = "Low-power",
    val stalenessResult: StalenessResult = StalenessResult(StalenessState.FRESH, 0, false, "Fresh"),
    val lastUpdateText: String = "Waiting for update",
    val mqttStatus: String = "Not configured",
    val webhookStatus: String = "Not configured",
    val notificationPermissionGranted: Boolean = true,
    val availableUpdate: com.batmon.app.updater.UpdateInfo? = null,
    val updateDismissed: Boolean = false
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BatMonApplication
    private val batteryRepo = app.batteryRepository
    private val settingsRepo = app.settingsRepository
    private val notificationService = app.notificationService
    private val mqttPublisher = app.mqttPublisher
    private val webhookDispatcher = app.webhookDispatcher
    private val webhookQueueRepo = app.webhookQueueRepository

    private var previousIsCharging: Boolean? = null

    private val initialBattery = batteryRepo.getCurrentState()

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            batteryPercent = initialBattery.percentage,
            chargingState = initialBattery.chargingStatus,
            stalenessResult = StaleDataDetector.evaluate(initialBattery.timestampMs),
            notificationPermissionGranted = notificationService.hasNotificationPermission()
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            app.updateManager.checkForUpdate().onSuccess { info ->
                if (info != null && info.isNewer) {
                    _uiState.update { it.copy(availableUpdate = info) }
                }
            }
        }

        viewModelScope.launch {
            combine(
                batteryRepo.batteryState,
                settingsRepo.settings,
                mqttPublisher.status,
                webhookQueueRepo.pendingQueue
            ) { battery, settings, mqttStatus, pendingWebhooks ->
                Quadruple(battery, settings, mqttStatus, pendingWebhooks)
            }.collect { (battery, settings, mqttConnStatus, pendingWebhooks) ->
                handleUpdate(battery, settings, mqttConnStatus, pendingWebhooks)
            }
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private suspend fun handleUpdate(
        battery: BatteryState,
        settings: BatMonSettings,
        mqttConnStatus: com.batmon.app.mqtt.MqttConnectionStatus,
        pendingWebhooks: List<com.batmon.app.webhooks.WebhookQueueItem>
    ) {
        val result = ThresholdEngine.evaluate(
            batteryPercent = battery.percentage,
            lowThreshold = settings.lowThreshold,
            highThreshold = settings.highThreshold,
            currentState = settings.thresholdState
        )

        if (result.nextState != settings.thresholdState) {
            settingsRepo.updateThresholdState(result.nextState)
        }

        if (settings.monitoringEnabled) {
            for (alert in result.triggeredAlerts) {
                when (alert) {
                    ThresholdAlert.LOW -> {
                        if (settings.notificationsEnabled) {
                            notificationService.showLowBatteryNotification(
                                percent = battery.percentage,
                                threshold = settings.lowThreshold,
                                chargingState = battery.chargingStatus
                            )
                        }
                        webhookDispatcher.enqueueAndProcess(
                            event = com.batmon.app.webhooks.WebhookEvent.BATTERY_LOW,
                            battery = battery,
                            settings = settings,
                            thresholdPercent = settings.lowThreshold
                        )
                        if (settings.mqtt.enabled && settings.mqtt.host.isNotBlank()) {
                            mqttPublisher.publishEvent("battery_low", battery, settings, settings.lowThreshold)
                        }
                    }
                    ThresholdAlert.HIGH -> {
                        if (settings.notificationsEnabled) {
                            notificationService.showHighBatteryNotification(
                                percent = battery.percentage,
                                threshold = settings.highThreshold,
                                chargingState = battery.chargingStatus
                            )
                        }
                        webhookDispatcher.enqueueAndProcess(
                            event = com.batmon.app.webhooks.WebhookEvent.BATTERY_HIGH,
                            battery = battery,
                            settings = settings,
                            thresholdPercent = settings.highThreshold
                        )
                        if (settings.mqtt.enabled && settings.mqtt.host.isNotBlank()) {
                            mqttPublisher.publishEvent("battery_high", battery, settings, settings.highThreshold)
                        }
                    }
                }
            }

            // Check charging state transitions
            val prevCharging = previousIsCharging
            if (prevCharging != null && prevCharging != battery.isCharging) {
                val chargingEvent = if (battery.isCharging) {
                    com.batmon.app.webhooks.WebhookEvent.CHARGING_STARTED
                } else {
                    com.batmon.app.webhooks.WebhookEvent.CHARGING_STOPPED
                }
                webhookDispatcher.enqueueAndProcess(
                    event = chargingEvent,
                    battery = battery,
                    settings = settings
                )
                if (settings.mqtt.enabled && settings.mqtt.host.isNotBlank()) {
                    val mqttEventName = if (battery.isCharging) "charging_started" else "charging_stopped"
                    mqttPublisher.publishEvent(mqttEventName, battery, settings)
                }
            }
            previousIsCharging = battery.isCharging
        }

        // Publish to MQTT if enabled
        if (settings.mqtt.enabled && settings.mqtt.host.isNotBlank() && settings.monitoringEnabled) {
            mqttPublisher.publishState(battery, settings)
        }

        val staleness = StaleDataDetector.evaluate(
            timestampMs = battery.timestampMs,
            isRealTimeMode = settings.realTimeMonitoringEnabled
        )

        val modeTitle = when {
            !settings.monitoringEnabled -> "Disabled"
            settings.realTimeMonitoringEnabled -> "Real-time"
            else -> "Low-power"
        }

        val mqttText = when {
            !settings.mqtt.enabled -> "Disabled"
            settings.mqtt.host.isBlank() -> "Not configured"
            mqttConnStatus is com.batmon.app.mqtt.MqttConnectionStatus.Connected -> "Connected (${settings.mqtt.host})"
            mqttConnStatus is com.batmon.app.mqtt.MqttConnectionStatus.Connecting -> "Connecting..."
            mqttConnStatus is com.batmon.app.mqtt.MqttConnectionStatus.Error -> "Error: ${mqttConnStatus.message}"
            else -> "Ready (${settings.mqtt.host})"
        }

        val webhookText = when {
            !settings.webhooks.enabled -> "Disabled"
            listOf(
                settings.webhooks.lowBatteryConfig,
                settings.webhooks.highBatteryConfig,
                settings.webhooks.chargingStartedConfig,
                settings.webhooks.chargingStoppedConfig,
                settings.webhooks.stateUpdateConfig
            ).none { it.enabled && it.url.isNotBlank() } -> "Not configured"
            pendingWebhooks.isNotEmpty() -> "Active (${pendingWebhooks.size} queued)"
            else -> "Active"
        }

        _uiState.value = DashboardUiState(
            batteryPercent = battery.percentage,
            chargingState = battery.chargingStatus,
            lowThreshold = settings.lowThreshold,
            highThreshold = settings.highThreshold,
            monitoringActive = settings.monitoringEnabled,
            monitoringModeTitle = modeTitle,
            stalenessResult = staleness,
            lastUpdateText = formatTime(battery.timestampMs),
            mqttStatus = mqttText,
            webhookStatus = webhookText,
            notificationPermissionGranted = notificationService.hasNotificationPermission()
        )
    }

    private fun formatTime(timestampMs: Long): String {
        val elapsedSeconds = (System.currentTimeMillis() - timestampMs) / 1000
        return when {
            elapsedSeconds < 5 -> "Just now"
            elapsedSeconds < 60 -> "${elapsedSeconds}s ago"
            else -> SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
        }
    }

    fun updateLowThreshold(low: Int) {
        viewModelScope.launch {
            settingsRepo.updateLowThreshold(low)
        }
    }

    fun updateHighThreshold(high: Int) {
        viewModelScope.launch {
            settingsRepo.updateHighThreshold(high)
        }
    }

    fun updateThresholds(low: Int, high: Int) {
        viewModelScope.launch {
            settingsRepo.updateThresholds(low, high)
        }
    }

    fun toggleMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setMonitoringEnabled(enabled)
            val currentSettings = settingsRepo.getSettings()
            BackgroundCoordinator.applyMonitoringMode(
                context = getApplication(),
                monitoringEnabled = enabled,
                realTimeEnabled = currentSettings.realTimeMonitoringEnabled
            )
        }
    }

    fun refreshPermissionStatus() {
        _uiState.value = _uiState.value.copy(
            notificationPermissionGranted = notificationService.hasNotificationPermission()
        )
    }

    fun dismissUpdate() {
        _uiState.update { it.copy(updateDismissed = true) }
    }

    fun openUpdate(context: android.content.Context, info: com.batmon.app.updater.UpdateInfo) {
        app.updateManager.openReleaseInBrowser(context, info.releaseUrl)
    }
}
