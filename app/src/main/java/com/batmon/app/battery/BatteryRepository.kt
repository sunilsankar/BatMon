package com.batmon.app.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class BatteryRepository(private val context: Context) {

    val batteryState: Flow<BatteryState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent != null && intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    trySend(parseBatteryIntent(intent))
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = context.registerReceiver(receiver, filter)
        if (initialIntent != null) {
            trySend(parseBatteryIntent(initialIntent))
        }

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }.conflate()

    fun getCurrentState(): BatteryState {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.let { parseBatteryIntent(it) } ?: BatteryState(
            percentage = 0,
            isCharging = false,
            chargingStatus = "Unknown"
        )
    }

    companion object {
        fun parseBatteryIntent(intent: Intent): BatteryState {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percentage = if (level >= 0 && scale > 0) {
                ((level * 100f) / scale).toInt().coerceIn(0, 100)
            } else {
                0
            }

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val chargingStatus = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> "Unknown"
            }

            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val pluggedSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                0 -> null
                else -> "Other"
            }

            val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val temperatureCelsius = if (rawTemp > 0) rawTemp / 10f else null

            val healthExtra = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val health = when (healthExtra) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> null
            }

            return BatteryState(
                percentage = percentage,
                isCharging = isCharging,
                chargingStatus = chargingStatus,
                pluggedSource = pluggedSource,
                temperatureCelsius = temperatureCelsius,
                health = health,
                timestampMs = System.currentTimeMillis()
            )
        }
    }
}
