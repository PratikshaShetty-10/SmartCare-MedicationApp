package com.yourteam.smartcare.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.yourteam.smartcare.MainActivity // Ensure this import is correct

class NotificationHelper(private val context: Context) {

    private val manager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        const val CHANNEL_REMINDER = "reminder_channel"
        const val CHANNEL_EMERGENCY = "emergency_channel"
        const val NOTIFICATION_ID_REMINDER = 1001
        const val NOTIFICATION_ID_EMERGENCY = 1002
        const val NOTIFICATION_ID_FOREGROUND = 1003 // For Worker persistence
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER, "Medication Reminders", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Reminders to take medication" }

            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical health emergencies"
                setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            manager.createNotificationChannels(listOf(reminderChannel, emergencyChannel))
        }
    }

    // --- Updated Methods ---

    fun showReminder(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID_REMINDER, notification)
    }

    // New method required by EscalationWorker (Stage 3)
    fun showEmergencyAlarm(title: String, message: String) {
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullScreenPendingIntent, true) // Forces app to foreground
            .setOngoing(true)
            .build()

        manager.notify(NOTIFICATION_ID_EMERGENCY, notification)
    }

    // New method required by EscalationWorker (Foreground Service)
    fun buildPersistentNotification(title: String, message: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .build()
    }

    fun cancelEmergency() {
        manager.cancel(NOTIFICATION_ID_EMERGENCY)
    }
}