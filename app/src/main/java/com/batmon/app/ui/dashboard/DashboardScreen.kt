package com.batmon.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    state: DashboardUiState = DashboardUiState(),
    onUpdateLowThreshold: (Int) -> Unit = {},
    onUpdateHighThreshold: (Int) -> Unit = {},
    onUpdateClick: (com.batmon.app.updater.UpdateInfo) -> Unit = {},
    onDismissUpdate: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BatMon", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.semantics { contentDescription = "Open Settings" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
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
        // ponytail: BoxWithConstraints for window size, material3-window-size-class if system window metrics needed
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isExpanded = maxWidth >= 600.dp

            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularBatteryGauge(
                            percentage = state.batteryPercent,
                            chargingState = state.chargingState,
                            size = 240.dp
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (state.availableUpdate != null && !state.updateDismissed) {
                            UpdateBanner(
                                updateInfo = state.availableUpdate,
                                onUpdateClick = { onUpdateClick(state.availableUpdate) },
                                onDismiss = onDismissUpdate
                            )
                        }
                        if (!state.notificationPermissionGranted) {
                            NotificationPermissionBanner()
                        }
                        if (state.stalenessResult.isUnsafeForAutomatedSwitching) {
                            StaleDataBanner(userSummary = state.stalenessResult.userSummary)
                        }
                        ThresholdSummaryCard(
                            lowThreshold = state.lowThreshold,
                            highThreshold = state.highThreshold,
                            onUpdateLowThreshold = onUpdateLowThreshold,
                            onUpdateHighThreshold = onUpdateHighThreshold
                        )
                        MonitoringStatusCard(
                            isActive = state.monitoringActive,
                            monitoringModeTitle = state.monitoringModeTitle,
                            freshnessSummary = state.stalenessResult.userSummary,
                            lastUpdate = state.lastUpdateText,
                            mqttStatus = state.mqttStatus,
                            webhookStatus = state.webhookStatus
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (state.availableUpdate != null && !state.updateDismissed) {
                        UpdateBanner(
                            updateInfo = state.availableUpdate,
                            onUpdateClick = { onUpdateClick(state.availableUpdate) },
                            onDismiss = onDismissUpdate
                        )
                    }
                    if (!state.notificationPermissionGranted) {
                        NotificationPermissionBanner()
                    }
                    if (state.stalenessResult.isUnsafeForAutomatedSwitching) {
                        StaleDataBanner(userSummary = state.stalenessResult.userSummary)
                    }
                    CircularBatteryGauge(
                        percentage = state.batteryPercent,
                        chargingState = state.chargingState,
                        size = 200.dp
                    )
                    ThresholdSummaryCard(
                        lowThreshold = state.lowThreshold,
                        highThreshold = state.highThreshold,
                        onUpdateLowThreshold = onUpdateLowThreshold,
                        onUpdateHighThreshold = onUpdateHighThreshold
                    )
                    MonitoringStatusCard(
                        isActive = state.monitoringActive,
                        monitoringModeTitle = state.monitoringModeTitle,
                        freshnessSummary = state.stalenessResult.userSummary,
                        lastUpdate = state.lastUpdateText,
                        mqttStatus = state.mqttStatus,
                        webhookStatus = state.webhookStatus
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Notifications are disabled. Grant permission in system settings to receive low/high threshold alerts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun StaleDataBanner(userSummary: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "$userSummary. Smart-plug switching is paused for safety.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun CircularBatteryGauge(
    percentage: Int,
    chargingState: String,
    size: Dp = 200.dp,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val gaugeColor = when {
        percentage <= 20 -> MaterialTheme.colorScheme.error
        percentage <= 45 -> Color(0xFFFFA726)
        else -> MaterialTheme.colorScheme.primary
    }

    val contentDesc = "Battery level $percentage%, charging state: $chargingState"

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = contentDesc }
    ) {
        Canvas(modifier = Modifier.size(size - 24.dp)) {
            val strokeWidth = 16.dp.toPx()
            // Track
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // Progress
            val sweep = (percentage.coerceIn(0, 100) / 100f) * 270f
            if (sweep > 0f) {
                drawArc(
                    color = gaugeColor,
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = chargingState,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ThresholdSummaryCard(
    lowThreshold: Int,
    highThreshold: Int,
    onUpdateLowThreshold: (Int) -> Unit = {},
    onUpdateHighThreshold: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var localLow by remember { mutableFloatStateOf(lowThreshold.toFloat()) }
    var localHigh by remember { mutableFloatStateOf(highThreshold.toFloat()) }
    var isDraggingLow by remember { mutableStateOf(false) }
    var isDraggingHigh by remember { mutableStateOf(false) }

    LaunchedEffect(lowThreshold) {
        if (!isDraggingLow) {
            localLow = lowThreshold.toFloat()
        }
    }
    LaunchedEffect(highThreshold) {
        if (!isDraggingHigh) {
            localHigh = highThreshold.toFloat()
        }
    }

    val displayLow = localLow.roundToInt().coerceIn(1, 98)
    val displayHigh = localHigh.roundToInt().coerceIn(2, 99)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Alert Thresholds",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Low Alert Dial / Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Low alert",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "≤ $displayLow%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = localLow.coerceIn(1f, 98f),
                    onValueChange = {
                        localLow = it
                        isDraggingLow = true
                    },
                    onValueChangeFinished = {
                        isDraggingLow = false
                        val rounded = localLow.roundToInt().coerceIn(1, 98)
                        if (rounded < highThreshold) {
                            onUpdateLowThreshold(rounded)
                        } else {
                            // Snap back if invalid (low must be < high)
                            localLow = lowThreshold.toFloat()
                        }
                    },
                    valueRange = 1f..98f,
                    steps = 0,
                    modifier = Modifier.semantics {
                        contentDescription = "Low battery alert threshold slider, currently at $displayLow percent"
                    }
                )
            }

            // High Alert Dial / Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "High alert",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "≥ $displayHigh%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = localHigh.coerceIn(2f, 99f),
                    onValueChange = {
                        localHigh = it
                        isDraggingHigh = true
                    },
                    onValueChangeFinished = {
                        isDraggingHigh = false
                        val rounded = localHigh.roundToInt().coerceIn(2, 99)
                        if (rounded > lowThreshold) {
                            onUpdateHighThreshold(rounded)
                        } else {
                            // Snap back if invalid (high must be > low)
                            localHigh = highThreshold.toFloat()
                        }
                    },
                    valueRange = 2f..99f,
                    steps = 0,
                    modifier = Modifier.semantics {
                        contentDescription = "High battery alert threshold slider, currently at $displayHigh percent"
                    }
                )
            }
        }
    }
}

@Composable
fun MonitoringStatusCard(
    isActive: Boolean,
    monitoringModeTitle: String,
    freshnessSummary: String,
    lastUpdate: String,
    mqttStatus: String,
    webhookStatus: String,
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
                text = "System Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            StatusRow(
                label = "Monitoring",
                value = if (isActive) "Active ($monitoringModeTitle)" else "Disabled"
            )
            StatusRow(
                label = "Data freshness",
                value = freshnessSummary
            )
            StatusRow(
                label = "Last update",
                value = lastUpdate
            )
            StatusRow(
                label = "MQTT Integration",
                value = mqttStatus
            )
            StatusRow(
                label = "Webhooks",
                value = webhookStatus
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun UpdateBanner(
    updateInfo: com.batmon.app.updater.UpdateInfo,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update Available",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Version ${updateInfo.tagName} is now available.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = onUpdateClick,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Update", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss update banner",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
