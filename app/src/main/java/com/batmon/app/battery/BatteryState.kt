package com.batmon.app.battery

data class BatteryState(
    val percentage: Int,
    val isCharging: Boolean,
    val chargingStatus: String,
    val pluggedSource: String? = null,
    val temperatureCelsius: Float? = null,
    val health: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
