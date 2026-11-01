package com.batmon.app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.batmon.app.MainActivity

class NotificationService(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_LOW_BATTERY = "channel_low_battery"
        const val CHANNEL_HIGH_BATTERY = "channel_high_battery"
        const val CHANNEL_MONITORING = "channel_monitoring"

        const val NOTIFICATION_ID_FOREGROUND = 1000
        const val NOTIFICATION_ID_LOW = 1001
        const val NOTIFICATION_ID_HIGH = 1002
    }

    init {
        createChannels()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitoringChannel = NotificationChannel(
                CHANNEL_MONITORING,
                "Real-Time Battery Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification indicating real-time background battery monitoring is active"
                setShowBadge(false)
            }

            val lowChannel = NotificationChannel(
                CHANNEL_LOW_BATTERY,
                "Low Battery Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when battery drops to or below the low threshold"
            }

            val highChannel = NotificationChannel(
                CHANNEL_HIGH_BATTERY,
                "High Battery Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when battery reaches or exceeds the high threshold"
            }

            notificationManager.createNotificationChannel(monitoringChannel)
            notificationManager.createNotificationChannel(lowChannel)
            notificationManager.createNotificationChannel(highChannel)
        }
    }

    fun buildForegroundNotification(percent: Int, chargingState: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("BatMon Active: $percent%")
            .setContentText("Status: $chargingState • Real-time monitoring")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun showLowBatteryNotification(percent: Int, threshold: Int, chargingState: String) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_LOW_BATTERY)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Low Battery: $percent%")
            .setContentText("Threshold: ≤$threshold% • State: $chargingState")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_LOW, notification)
    }

    fun showHighBatteryNotification(percent: Int, threshold: Int, chargingState: String) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_HIGH_BATTERY)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("High Battery: $percent%")
            .setContentText("Threshold: ≥$threshold% • State: $chargingState")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_HIGH, notification)
    }
}
