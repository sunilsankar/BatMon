package com.batmon.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batmon.app.BatMonApplication
import com.batmon.app.background.BackgroundCoordinator
import com.batmon.app.battery.ThresholdEngine
import com.batmon.app.data.BatMonSettings
import com.batmon.app.diagnostics.DiagnosticsInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BatMonApplication
    private val settingsRepo = app.settingsRepository
    private val batteryRepo = app.batteryRepository
    private val diagnosticsRepo = app.diagnosticsRepository
    private val mqttPublisher = app.mqttPublisher
    private val webhookQueueRepo = app.webhookQueueRepository
    private val webhookDispatcher = app.webhookDispatcher

    val settings: StateFlow<BatMonSettings> = settingsRepo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BatMonSettings()
    )

    val deliveryHistory: StateFlow<List<com.batmon.app.webhooks.WebhookQueueItem>> = webhookQueueRepo.deliveryHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val pendingQueue: StateFlow<List<com.batmon.app.webhooks.WebhookQueueItem>> = webhookQueueRepo.pendingQueue.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _mqttTestStatus = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val mqttTestStatus: StateFlow<String?> = _mqttTestStatus

    private val _isTestingMqtt = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isTestingMqtt: StateFlow<Boolean> = _isTestingMqtt

    private val _webhookTestStatus = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val webhookTestStatus: StateFlow<String?> = _webhookTestStatus

    private val _isTestingWebhook = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isTestingWebhook: StateFlow<Boolean> = _isTestingWebhook

    private val _updateState = kotlinx.coroutines.flow.MutableStateFlow<com.batmon.app.updater.UpdateState>(com.batmon.app.updater.UpdateState.Idle)
    val updateState: StateFlow<com.batmon.app.updater.UpdateState> = _updateState

    val diagnostics: StateFlow<DiagnosticsInfo?> = combine(
        settingsRepo.settings,
        batteryRepo.batteryState
    ) { currentSettings, battery ->
        diagnosticsRepo.getDiagnostics(currentSettings, battery.timestampMs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

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

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setNotificationsEnabled(enabled)
        }
    }

    fun setRealTimeMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setRealTimeMonitoringEnabled(enabled)
            val currentSettings = settingsRepo.getSettings()
            BackgroundCoordinator.applyMonitoringMode(
                context = getApplication(),
                monitoringEnabled = currentSettings.monitoringEnabled,
                realTimeEnabled = enabled
            )
        }
    }

    fun setMqttEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setMqttEnabled(enabled)
        }
    }

    fun updateMqttSettings(mqtt: com.batmon.app.data.MqttSettings) {
        viewModelScope.launch {
            settingsRepo.updateMqttSettings(mqtt)
        }
    }

    fun testMqttConnection(mqtt: com.batmon.app.data.MqttSettings) {
        viewModelScope.launch {
            _isTestingMqtt.value = true
            _mqttTestStatus.value = "Testing broker connection & publishing state..."
            settingsRepo.updateMqttSettings(mqtt)
            val currentSettings = settings.value.copy(mqtt = mqtt)
            val batteryState = app.batteryRepository.getCurrentState()
            val result = mqttPublisher.testConnection(currentSettings, batteryState)
            result.fold(
                onSuccess = { msg ->
                    _mqttTestStatus.value = "Success: $msg"
                },
                onFailure = { err ->
                    _mqttTestStatus.value = "Failed: ${err.message}"
                }
            )
            _isTestingMqtt.value = false
        }
    }

    fun publishMqttDiscovery(mqtt: com.batmon.app.data.MqttSettings? = null) {
        viewModelScope.launch {
            _mqttTestStatus.value = "Publishing Home Assistant discovery..."
            if (mqtt != null) {
                settingsRepo.updateMqttSettings(mqtt)
            }
            val currentSettings = if (mqtt != null) settings.value.copy(mqtt = mqtt) else settings.value
            val result = mqttPublisher.publishDiscovery(currentSettings)
            result.fold(
                onSuccess = {
                    _mqttTestStatus.value = "HA discovery published successfully"
                },
                onFailure = { err ->
                    _mqttTestStatus.value = "Discovery error: ${err.message}"
                }
            )
        }
    }

    fun setWebhooksEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setWebhooksEnabled(enabled)
        }
    }

    fun setAllowInsecureHttp(allow: Boolean) {
        viewModelScope.launch {
            val current = settings.value.webhooks
            settingsRepo.updateWebhookSettings(current.copy(allowInsecureHttp = allow))
        }
    }

    fun updateWebhookEventConfig(event: com.batmon.app.webhooks.WebhookEvent, config: com.batmon.app.webhooks.WebhookEventConfig) {
        viewModelScope.launch {
            val current = settings.value.webhooks
            val updated = when (event) {
                com.batmon.app.webhooks.WebhookEvent.BATTERY_LOW -> current.copy(lowBatteryConfig = config)
                com.batmon.app.webhooks.WebhookEvent.BATTERY_HIGH -> current.copy(highBatteryConfig = config)
                com.batmon.app.webhooks.WebhookEvent.CHARGING_STARTED -> current.copy(chargingStartedConfig = config)
                com.batmon.app.webhooks.WebhookEvent.CHARGING_STOPPED -> current.copy(chargingStoppedConfig = config)
                com.batmon.app.webhooks.WebhookEvent.STATE_UPDATE -> current.copy(stateUpdateConfig = config)
                com.batmon.app.webhooks.WebhookEvent.MONITORING_WARNING -> current.copy(monitoringWarningConfig = config)
            }
            settingsRepo.updateWebhookSettings(updated)
        }
    }

    fun testWebhookDelivery(
        event: com.batmon.app.webhooks.WebhookEvent,
        config: com.batmon.app.webhooks.WebhookEventConfig
    ) {
        viewModelScope.launch {
            _isTestingWebhook.value = true
            _webhookTestStatus.value = "Testing webhook delivery for ${event.displayName}..."
            val currentSettings = settings.value
            val battery = batteryRepo.getCurrentState()
            val payload = com.batmon.app.webhooks.WebhookPayloadFactory.buildPayload(
                event = event,
                batteryState = battery,
                deviceId = currentSettings.deviceId,
                deviceName = currentSettings.deviceName,
                thresholdPercent = if (event == com.batmon.app.webhooks.WebhookEvent.BATTERY_LOW) currentSettings.lowThreshold else currentSettings.highThreshold,
                template = config.payloadTemplate
            )
            val result = webhookDispatcher.testDelivery(
                url = config.url,
                method = config.httpMethod,
                headers = config.headers,
                payload = payload,
                allowInsecureHttp = currentSettings.webhooks.allowInsecureHttp,
                timeoutSeconds = config.timeoutSeconds
            )
            result.fold(
                onSuccess = { statusCode ->
                    _webhookTestStatus.value = "Success: HTTP $statusCode"
                },
                onFailure = { err ->
                    _webhookTestStatus.value = "Failed: ${err.message}"
                }
            )
            _isTestingWebhook.value = false
        }
    }

    fun retryPendingWebhooks() {
        viewModelScope.launch {
            val currentSettings = settings.value
            val processed = webhookDispatcher.processPendingQueue(currentSettings.webhooks.allowInsecureHttp)
            _webhookTestStatus.value = "Processed $processed pending webhook(s)"
        }
    }

    fun clearWebhookHistory() {
        viewModelScope.launch {
            webhookQueueRepo.clearHistory()
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateState.value = com.batmon.app.updater.UpdateState.Checking
            app.updateManager.checkForUpdate()
                .onSuccess { info ->
                    _updateState.value = if (info != null && info.isNewer) {
                        com.batmon.app.updater.UpdateState.Available(info)
                    } else {
                        com.batmon.app.updater.UpdateState.UpToDate(com.batmon.app.BuildConfig.VERSION_NAME)
                    }
                }
                .onFailure { error ->
                    _updateState.value = com.batmon.app.updater.UpdateState.Error(error.message ?: "Failed to check for updates")
                }
        }
    }

    fun openRelease(context: android.content.Context, url: String) {
        app.updateManager.openReleaseInBrowser(context, url)
    }

    fun downloadAndInstallUpdate(context: android.content.Context, downloadUrl: String) {
        viewModelScope.launch {
            _updateState.value = com.batmon.app.updater.UpdateState.Downloading(0f)
            app.updateManager.downloadApk(context, downloadUrl) { progress ->
                _updateState.value = com.batmon.app.updater.UpdateState.Downloading(progress)
            }.onSuccess { apkFile ->
                _updateState.value = com.batmon.app.updater.UpdateState.ReadyToInstall(apkFile)
                app.updateManager.installApk(context, apkFile)
            }.onFailure { error ->
                _updateState.value = com.batmon.app.updater.UpdateState.Error(error.message ?: "Download failed")
            }
        }
    }
}
