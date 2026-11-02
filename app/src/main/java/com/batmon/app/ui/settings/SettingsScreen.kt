package com.batmon.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.batmon.app.data.BatMonSettings
import com.batmon.app.data.MqttSettings
import com.batmon.app.diagnostics.DiagnosticsInfo
import com.batmon.app.util.trimBlanks
import com.batmon.app.webhooks.DeliveryStatus
import com.batmon.app.webhooks.WebhookEvent
import com.batmon.app.webhooks.WebhookEventConfig
import com.batmon.app.webhooks.WebhookQueueItem
import com.batmon.app.webhooks.WebhookSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: BatMonSettings,
    diagnostics: DiagnosticsInfo?,
    mqttTestStatus: String? = null,
    isTestingMqtt: Boolean = false,
    webhookTestStatus: String? = null,
    isTestingWebhook: Boolean = false,
    deliveryHistory: List<WebhookQueueItem> = emptyList(),
    pendingQueue: List<WebhookQueueItem> = emptyList(),
    onToggleNotifications: (Boolean) -> Unit,
    onToggleRealTimeMonitoring: (Boolean) -> Unit,
    onUpdateMqttSettings: (MqttSettings) -> Unit = {},
    onTestMqttConnection: (MqttSettings) -> Unit = {},
    onPublishMqttDiscovery: () -> Unit = {},
    onToggleWebhooks: (Boolean) -> Unit = {},
    onToggleAllowInsecureHttp: (Boolean) -> Unit = {},
    onUpdateWebhookEventConfig: (WebhookEvent, WebhookEventConfig) -> Unit = { _, _ -> },
    onTestWebhookDelivery: (WebhookEvent, WebhookEventConfig) -> Unit = { _, _ -> },
    onRetryPendingWebhooks: () -> Unit = {},
    onClearWebhookHistory: () -> Unit = {},
    updateState: com.batmon.app.updater.UpdateState = com.batmon.app.updater.UpdateState.Idle,
    onCheckForUpdates: () -> Unit = {},
    onDownloadAndInstallUpdate: (String) -> Unit = {},
    onOpenRelease: (String) -> Unit = {},
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.semantics { contentDescription = "Navigate back to Dashboard" }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isTablet = maxWidth >= 840.dp

            if (isTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        NotificationsCard(
                            settings = settings,
                            onToggleNotifications = onToggleNotifications
                        )
                        RealTimeModeCard(
                            settings = settings,
                            onToggleRealTimeMonitoring = onToggleRealTimeMonitoring
                        )
                        DeviceDiagnosticsCard(
                            settings = settings,
                            diagnostics = diagnostics
                        )
                        AboutCard(
                            updateState = updateState,
                            onCheckForUpdates = onCheckForUpdates,
                            onDownloadAndInstallUpdate = onDownloadAndInstallUpdate,
                            onOpenRelease = onOpenRelease
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MqttSettingsCard(
                            settings = settings,
                            mqttTestStatus = mqttTestStatus,
                            isTestingMqtt = isTestingMqtt,
                            onUpdateMqttSettings = onUpdateMqttSettings,
                            onTestConnection = onTestMqttConnection,
                            onPublishDiscovery = onPublishMqttDiscovery
                        )

                        WebhooksSettingsCard(
                            settings = settings,
                            webhookTestStatus = webhookTestStatus,
                            isTestingWebhook = isTestingWebhook,
                            deliveryHistory = deliveryHistory,
                            pendingQueue = pendingQueue,
                            onToggleWebhooks = onToggleWebhooks,
                            onToggleAllowInsecureHttp = onToggleAllowInsecureHttp,
                            onUpdateEventConfig = onUpdateWebhookEventConfig,
                            onTestDelivery = onTestWebhookDelivery,
                            onRetryPending = onRetryPendingWebhooks,
                            onClearHistory = onClearWebhookHistory
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    NotificationsCard(
                        settings = settings,
                        onToggleNotifications = onToggleNotifications
                    )
                    RealTimeModeCard(
                        settings = settings,
                        onToggleRealTimeMonitoring = onToggleRealTimeMonitoring
                    )
                    DeviceDiagnosticsCard(
                        settings = settings,
                        diagnostics = diagnostics
                    )
                    MqttSettingsCard(
                        settings = settings,
                        mqttTestStatus = mqttTestStatus,
                        isTestingMqtt = isTestingMqtt,
                        onUpdateMqttSettings = onUpdateMqttSettings,
                        onTestConnection = onTestMqttConnection,
                        onPublishDiscovery = onPublishMqttDiscovery
                    )
                    WebhooksSettingsCard(
                        settings = settings,
                        webhookTestStatus = webhookTestStatus,
                        isTestingWebhook = isTestingWebhook,
                        deliveryHistory = deliveryHistory,
                        pendingQueue = pendingQueue,
                        onToggleWebhooks = onToggleWebhooks,
                        onToggleAllowInsecureHttp = onToggleAllowInsecureHttp,
                        onUpdateEventConfig = onUpdateWebhookEventConfig,
                        onTestDelivery = onTestWebhookDelivery,
                        onRetryPending = onRetryPendingWebhooks,
                        onClearHistory = onClearWebhookHistory
                    )
                    AboutCard(
                        updateState = updateState,
                        onCheckForUpdates = onCheckForUpdates,
                        onDownloadAndInstallUpdate = onDownloadAndInstallUpdate,
                        onOpenRelease = onOpenRelease
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun NotificationsCard(
    settings: BatMonSettings,
    onToggleNotifications: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Post local notifications on threshold crossing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.notificationsEnabled,
                onCheckedChange = onToggleNotifications,
                modifier = Modifier.semantics { contentDescription = "Toggle local notifications" }
            )
        }
    }
}

@Composable
private fun RealTimeModeCard(
    settings: BatMonSettings,
    onToggleRealTimeMonitoring: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Real-Time Monitoring",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (settings.realTimeMonitoringEnabled)
                            "Foreground service active with persistent notification"
                        else
                            "Low-power mode active (periodic sync ~15m)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.realTimeMonitoringEnabled,
                    onCheckedChange = onToggleRealTimeMonitoring,
                    modifier = Modifier.semantics { contentDescription = "Toggle real-time foreground service monitoring" }
                )
            }
            Text(
                text = "Real-time mode uses an ongoing notification to prevent Android from deferring battery checks. Low-power mode saves battery but events may be delayed by Doze.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceDiagnosticsCard(
    settings: BatMonSettings,
    diagnostics: DiagnosticsInfo?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Device Identity & Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            DiagnosticsRow("Device ID", settings.deviceId)
            DiagnosticsRow("Device Name", settings.deviceName)
            diagnostics?.let { diag ->
                DiagnosticsRow("Android OS", "${diag.androidRelease} (API ${diag.apiLevel})")
                DiagnosticsRow("Active Mode", diag.monitoringMode)
                DiagnosticsRow("Power Save Mode", if (diag.isPowerSaveMode) "Active" else "Normal")
                DiagnosticsRow(
                    "Battery Optimization",
                    if (diag.isIgnoringBatteryOptimizations) "Exempted" else "Optimized (may delay sync)"
                )
                DiagnosticsRow("Data Freshness", diag.stalenessResult.userSummary)

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Platform Background Limitations",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                for (limitation in diag.limitations) {
                    Text(
                        text = "• $limitation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MqttSettingsCard(
    settings: BatMonSettings,
    mqttTestStatus: String?,
    isTestingMqtt: Boolean,
    onUpdateMqttSettings: (MqttSettings) -> Unit,
    onTestConnection: (MqttSettings) -> Unit,
    onPublishDiscovery: () -> Unit
) {
    var enabled by remember(settings.mqtt.enabled) { mutableStateOf(settings.mqtt.enabled) }
    var host by remember(settings.mqtt.host) { mutableStateOf(settings.mqtt.host) }
    var portText by remember(settings.mqtt.port) { mutableStateOf(settings.mqtt.port.toString()) }
    var username by remember(settings.mqtt.username) { mutableStateOf(settings.mqtt.username) }
    var password by remember(settings.mqtt.password) { mutableStateOf(settings.mqtt.password) }
    var topicPrefix by remember(settings.mqtt.topicPrefix) { mutableStateOf(settings.mqtt.topicPrefix) }
    var discoveryEnabled by remember(settings.mqtt.discoveryEnabled) { mutableStateOf(settings.mqtt.discoveryEnabled) }
    var passwordVisible by remember { mutableStateOf(false) }

    fun currentMqtt(): MqttSettings = MqttSettings(
        enabled = enabled,
        host = host.trim(),
        port = portText.trim().toIntOrNull() ?: 1883,
        username = username.trimBlanks(),
        password = password.trimBlanks(),
        topicPrefix = topicPrefix.trim(),
        discoveryEnabled = discoveryEnabled
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MQTT / Home Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Publish battery state and auto-discover entities in HA",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        onUpdateMqttSettings(currentMqtt().copy(enabled = it))
                    }
                )
            }

            if (enabled) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Broker Host / IP") },
                    placeholder = { Text("e.g. 192.168.1.100 or homeassistant.local") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Uri
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text("Port") },
                    placeholder = { Text("1883") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (optional)") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = if (password.isNotEmpty()) {
                        {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    painter = painterResource(android.R.drawable.ic_menu_view),
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Password
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = topicPrefix,
                    onValueChange = { topicPrefix = it },
                    label = { Text("Topic Prefix") },
                    placeholder = { Text("batmon") },
                    supportingText = {
                        Text("Prefix for MQTT topics (default: batmon)")
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Ascii
                    ),
                    trailingIcon = if (topicPrefix.isNotBlank()) {
                        {
                            IconButton(onClick = { topicPrefix = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Reset topic prefix to default")
                            }
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val resolvedClientId = currentMqtt().effectiveClientId(settings.deviceId)
                val resolvedStatusTopic = currentMqtt().statusTopic(settings.deviceId)
                val resolvedBatteryTopic = currentMqtt().metricTopic(settings.deviceId, "battery_level")
                val resolvedStateTopic = currentMqtt().stateTopic(settings.deviceId)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Resolved Publish Topics & Client ID",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• Client ID: $resolvedClientId",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Availability / LWT: $resolvedStatusTopic",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• Telemetry: $resolvedBatteryTopic, ...",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "• State JSON: $resolvedStateTopic",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Home Assistant Discovery", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Auto-registers battery, charging, and diagnostic entities",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = discoveryEnabled,
                        onCheckedChange = { discoveryEnabled = it }
                    )
                }

                mqttTestStatus?.let { status ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (status.startsWith("Connected to") || status.startsWith("Success") || status.contains("successfully"))
                                MaterialTheme.colorScheme.primaryContainer
                            else if (status.startsWith("Cannot reach") || status.startsWith("Broker reached, but authentication failed") || status.startsWith("Failed") || status.contains("error"))
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = status,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onUpdateMqttSettings(currentMqtt()) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Settings")
                    }

                    Button(
                        onClick = { onTestConnection(currentMqtt()) },
                        enabled = !isTestingMqtt && host.isNotBlank(),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        if (isTestingMqtt) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.height(16.dp)
                            )
                        } else {
                            Text("Test Connection")
                        }
                    }
                }

                if (discoveryEnabled) {
                    OutlinedButton(
                        onClick = {
                            onUpdateMqttSettings(currentMqtt())
                            onPublishDiscovery()
                        },
                        enabled = host.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Publish HA Discovery Now")
                    }
                }
            }
        }
    }
}

@Composable
fun WebhooksSettingsCard(
    settings: BatMonSettings,
    webhookTestStatus: String?,
    isTestingWebhook: Boolean,
    deliveryHistory: List<WebhookQueueItem>,
    pendingQueue: List<WebhookQueueItem>,
    onToggleWebhooks: (Boolean) -> Unit,
    onToggleAllowInsecureHttp: (Boolean) -> Unit,
    onUpdateEventConfig: (WebhookEvent, WebhookEventConfig) -> Unit,
    onTestDelivery: (WebhookEvent, WebhookEventConfig) -> Unit,
    onRetryPending: () -> Unit,
    onClearHistory: () -> Unit
) {
    var selectedEvent by remember { mutableStateOf(WebhookEvent.BATTERY_LOW) }
    val currentConfig = settings.webhooks.getConfigForEvent(selectedEvent)

    var enabled by remember(selectedEvent, currentConfig) { mutableStateOf(currentConfig.enabled) }
    var url by remember(selectedEvent, currentConfig) { mutableStateOf(currentConfig.url) }
    var httpMethod by remember(selectedEvent, currentConfig) { mutableStateOf(currentConfig.httpMethod) }
    var headers by remember(selectedEvent, currentConfig) { mutableStateOf(currentConfig.headers) }
    var payloadTemplate by remember(selectedEvent, currentConfig) { mutableStateOf(currentConfig.payloadTemplate) }
    var maxRetriesText by remember(selectedEvent, currentConfig) { mutableStateOf(currentConfig.maxRetries.toString()) }

    fun activeConfig(): WebhookEventConfig = WebhookEventConfig(
        enabled = enabled,
        url = url.trim(),
        httpMethod = httpMethod,
        headers = headers,
        payloadTemplate = payloadTemplate,
        maxRetries = maxRetriesText.toIntOrNull() ?: 3
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HTTP Webhooks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Deliver JSON events directly to HTTP/HTTPS webhooks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.webhooks.enabled,
                    onCheckedChange = onToggleWebhooks
                )
            }

            if (settings.webhooks.enabled) {
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow Insecure HTTP",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "HTTPS is enforced by default. Only enable for local networks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.webhooks.allowInsecureHttp,
                        onCheckedChange = onToggleAllowInsecureHttp
                    )
                }

                if (settings.webhooks.allowInsecureHttp) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = "Security Warning: Insecure HTTP sends battery telemetry and custom headers in cleartext over the network.",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Enabled Events (Choose Multiple)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Select all events that should trigger an HTTP webhook:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Multi-select event chips across all 6 events
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        listOf(
                            WebhookEvent.BATTERY_LOW to "Low Battery",
                            WebhookEvent.BATTERY_HIGH to "High Battery"
                        ),
                        listOf(
                            WebhookEvent.CHARGING_STARTED to "Plugged In",
                            WebhookEvent.CHARGING_STOPPED to "Unplugged"
                        ),
                        listOf(
                            WebhookEvent.STATE_UPDATE to "State Update",
                            WebhookEvent.MONITORING_WARNING to "Warning"
                        )
                    ).forEach { rowEvents ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowEvents.forEach { (event, label) ->
                                val isEventEnabled = settings.webhooks.getConfigForEvent(event).enabled
                                FilterChip(
                                    selected = isEventEnabled,
                                    onClick = {
                                        val existing = settings.webhooks.getConfigForEvent(event)
                                        val updated = existing.copy(enabled = !isEventEnabled)
                                        onUpdateEventConfig(event, updated)
                                        if (selectedEvent == event) {
                                            enabled = !isEventEnabled
                                        }
                                    },
                                    label = { Text(label) },
                                    leadingIcon = if (isEventEnabled) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Configure Event Destination & Payload",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                // Tabs for editing individual event configuration
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        listOf(
                            WebhookEvent.BATTERY_LOW to "Low Bat",
                            WebhookEvent.BATTERY_HIGH to "High Bat",
                            WebhookEvent.CHARGING_STARTED to "Plugged"
                        ),
                        listOf(
                            WebhookEvent.CHARGING_STOPPED to "Unplugged",
                            WebhookEvent.STATE_UPDATE to "State",
                            WebhookEvent.MONITORING_WARNING to "Warning"
                        )
                    ).forEach { rowEvents ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowEvents.forEach { (event, label) ->
                                val isSelected = selectedEvent == event
                                val isEventEnabled = settings.webhooks.getConfigForEvent(event).enabled
                                if (isSelected) {
                                    Button(
                                        onClick = { selectedEvent = event },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { selectedEvent = event },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (isEventEnabled) "✓ $label" else label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable for ${selectedEvent.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            onUpdateEventConfig(selectedEvent, activeConfig().copy(enabled = it))
                        }
                    )
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Webhook URL") },
                    placeholder = { Text("https://homeassistant.local:8123/api/webhook/batmon") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        keyboardType = KeyboardType.Uri
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = httpMethod,
                        onValueChange = { httpMethod = it },
                        label = { Text("Method") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxRetriesText,
                        onValueChange = { maxRetriesText = it },
                        label = { Text("Max Retries") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    label = { Text("Custom Headers (one per line)") },
                    placeholder = { Text("Authorization: Bearer my-token") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = payloadTemplate,
                    onValueChange = { payloadTemplate = it },
                    label = { Text("Payload Template (optional)") },
                    placeholder = { Text("Leave blank for standard JSON") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        val currentConfig = activeConfig()
                        WebhookEvent.entries.forEach { ev ->
                            val existing = settings.webhooks.getConfigForEvent(ev)
                            if (existing.enabled) {
                                onUpdateEventConfig(
                                    ev,
                                    existing.copy(
                                        url = currentConfig.url,
                                        httpMethod = currentConfig.httpMethod,
                                        headers = currentConfig.headers,
                                        maxRetries = currentConfig.maxRetries
                                    )
                                )
                            }
                        }
                    },
                    enabled = url.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply this URL & headers to all enabled events")
                }

                webhookTestStatus?.let { status ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (status.startsWith("Success"))
                                MaterialTheme.colorScheme.primaryContainer
                            else if (status.startsWith("Failed"))
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = status,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onUpdateEventConfig(selectedEvent, activeConfig()) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Config")
                    }

                    Button(
                        onClick = { onTestDelivery(selectedEvent, activeConfig()) },
                        enabled = !isTestingWebhook && url.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTestingWebhook) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.height(16.dp)
                            )
                        } else {
                            Text("Test Webhook")
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Durable Queue & Delivery History",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pending in queue: ${pendingQueue.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (pendingQueue.isNotEmpty()) {
                        OutlinedButton(onClick = onRetryPending) {
                            Text("Retry Now")
                        }
                    }
                }

                if (deliveryHistory.isNotEmpty()) {
                    Text(
                        text = "Recent Deliveries (last ${deliveryHistory.take(5).size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    deliveryHistory.take(5).forEach { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.event,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val (statusColor, statusText) = when (item.status) {
                                        DeliveryStatus.SUCCESS -> MaterialTheme.colorScheme.primary to "HTTP ${item.responseCode ?: 200} OK"
                                        DeliveryStatus.FAILED_PERMANENT -> MaterialTheme.colorScheme.error to (item.lastError ?: "Failed")
                                        DeliveryStatus.FAILED_TRANSIENT -> MaterialTheme.colorScheme.tertiary to "Retrying (${item.attemptCount}/${item.maxRetries})"
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant to item.status.name
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = onClearHistory) {
                            Text("Clear History")
                        }
                    }
                } else {
                    Text(
                        text = "No webhook deliveries recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutCard(
    updateState: com.batmon.app.updater.UpdateState,
    onCheckForUpdates: () -> Unit,
    onDownloadAndInstallUpdate: (String) -> Unit,
    onOpenRelease: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(6.dp)
                    )
                }
                Text(
                    text = "About BatMon",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Version",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "v${com.batmon.app.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        enabled = updateState !is com.batmon.app.updater.UpdateState.Checking &&
                                updateState !is com.batmon.app.updater.UpdateState.Downloading,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Check Updates",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Author",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Sunil Sankar",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Source Code",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "GitHub",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onOpenRelease("https://github.com/sunilsankar/BatMon") }
                        .semantics { contentDescription = "Open BatMon GitHub repository" }
                )
            }

            when (updateState) {
                is com.batmon.app.updater.UpdateState.Idle -> {
                    // Check button is placed inline next to Version
                }
                is com.batmon.app.updater.UpdateState.Checking -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking for updates...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                is com.batmon.app.updater.UpdateState.Available -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "New version available: ${updateState.info.tagName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onDownloadAndInstallUpdate(updateState.info.downloadUrl) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Download & Install")
                            }
                            OutlinedButton(
                                onClick = { onOpenRelease(updateState.info.releaseUrl) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("View on GitHub")
                            }
                        }
                    }
                }
                is com.batmon.app.updater.UpdateState.UpToDate -> {
                    Text(
                        text = "BatMon is up to date (${updateState.currentVersion})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is com.batmon.app.updater.UpdateState.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Downloading update...", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { updateState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is com.batmon.app.updater.UpdateState.ReadyToInstall -> {
                    Text(
                        text = "Download complete. Starting installer...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is com.batmon.app.updater.UpdateState.Error -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Check failed: ${updateState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onCheckForUpdates,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Retry", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Disclaimer: BatMon only monitors and reports battery status. It cannot switch a charger by itself — pair it with a smart plug and Home Assistant automation to act on low/high battery alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}
