package com.yourteam.smartcare.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_REMINDER = "reminder_channel"
        const val CHANNEL_EMERGENCY = "emergency_channel"
        const val NOTIFICATION_ID_REMINDER = 1001
        const val NOTIFICATION_ID_EMERGENCY = 1002
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER,
                "Medication Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to take medication"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            manager.createNotificationChannel(reminderChannel)

            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical health emergencies"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            }
            manager.createNotificationChannel(emergencyChannel)
        }
    }

    fun showReminder(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_REMINDER, notification)
    }

    fun showEmergency(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_EMERGENCY, notification)
    }
}