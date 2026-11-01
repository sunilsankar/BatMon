package com.batmon.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.batmon.app.ui.dashboard.DashboardScreen
import com.batmon.app.ui.dashboard.DashboardViewModel
import com.batmon.app.ui.settings.SettingsScreen
import com.batmon.app.ui.settings.SettingsViewModel

object Destinations {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
}

@Composable
fun BatMonNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.DASHBOARD
    ) {
        composable(Destinations.DASHBOARD) {
            val viewModel: DashboardViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current
            DashboardScreen(
                state = state,
                onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                onUpdateLowThreshold = { viewModel.updateLowThreshold(it) },
                onUpdateHighThreshold = { viewModel.updateHighThreshold(it) },
                onUpdateClick = { viewModel.openUpdate(context, it) },
                onDismissUpdate = { viewModel.dismissUpdate() }
            )
        }
        composable(Destinations.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
            val mqttTestStatus by viewModel.mqttTestStatus.collectAsStateWithLifecycle()
            val isTestingMqtt by viewModel.isTestingMqtt.collectAsStateWithLifecycle()
            val webhookTestStatus by viewModel.webhookTestStatus.collectAsStateWithLifecycle()
            val isTestingWebhook by viewModel.isTestingWebhook.collectAsStateWithLifecycle()
            val deliveryHistory by viewModel.deliveryHistory.collectAsStateWithLifecycle()
            val pendingQueue by viewModel.pendingQueue.collectAsStateWithLifecycle()
            val updateState by viewModel.updateState.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current
            SettingsScreen(
                settings = settings,
                diagnostics = diagnostics,
                mqttTestStatus = mqttTestStatus,
                isTestingMqtt = isTestingMqtt,
                webhookTestStatus = webhookTestStatus,
                isTestingWebhook = isTestingWebhook,
                deliveryHistory = deliveryHistory,
                pendingQueue = pendingQueue,
                updateState = updateState,
                onCheckForUpdates = { viewModel.checkForUpdates() },
                onDownloadAndInstallUpdate = { viewModel.downloadAndInstallUpdate(context, it) },
                onOpenRelease = { viewModel.openRelease(context, it) },
                onToggleNotifications = { viewModel.setNotificationsEnabled(it) },
                onToggleRealTimeMonitoring = { viewModel.setRealTimeMonitoringEnabled(it) },
                onUpdateMqttSettings = { viewModel.updateMqttSettings(it) },
                onTestMqttConnection = { viewModel.testMqttConnection(it) },
                onPublishMqttDiscovery = { viewModel.publishMqttDiscovery() },
                onToggleWebhooks = { viewModel.setWebhooksEnabled(it) },
                onToggleAllowInsecureHttp = { viewModel.setAllowInsecureHttp(it) },
                onUpdateWebhookEventConfig = { event, config -> viewModel.updateWebhookEventConfig(event, config) },
                onTestWebhookDelivery = { event, config -> viewModel.testWebhookDelivery(event, config) },
                onRetryPendingWebhooks = { viewModel.retryPendingWebhooks() },
                onClearWebhookHistory = { viewModel.clearWebhookHistory() },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
