package com.yourteam.smartcare.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.yourteam.smartcare.MainActivity

/**
 * Notification helper for both existing SmartCare notifications
 * and the new medication escalation system.
 *
 * Existing SOS/FCM-related functionality is preserved.
 * Medication escalation uses separate channels and notification IDs.
 */
class NotificationHelper(private val context: Context) {

    private val manager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    companion object {
        // ---- Existing SmartCare notifications ----
        const val CHANNEL_REMINDER = "reminder_channel"
        const val CHANNEL_EMERGENCY = "emergency_channel"

        const val NOTIFICATION_ID_REMINDER = 1001
        const val NOTIFICATION_ID_EMERGENCY = 1002
        const val NOTIFICATION_ID_FOREGROUND = 1003

        // ---- Medication escalation ----
        const val CHANNEL_DOSE_REMINDER = "dose_reminder_v1"
        const val CHANNEL_DOSE_SILENT = "dose_silent_v1"
        const val CHANNEL_DOSE_ALARM_FALLBACK = "dose_alarm_fallback_v1"
        const val CHANNEL_DOSE_SERVICE = "dose_service_v1"
        const val CHANNEL_GUARDIAN_ALERT = "guardian_alert_v1"

        const val NOTIFICATION_ID_DOSE_SERVICE = 4001

        /**
         * Creates a stable notification ID for each medication occurrence.
         * Different doses will not overwrite each other.
         */
        fun doseNotificationId(occId: String): Int =
            10_000 + (occId.hashCode() and 0x3FFFFFFF)
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // ============================================================
        // EXISTING CHANNELS
        // ============================================================

        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDER,
            "Medication Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders to take medication"
        }

        val emergencyChannel = NotificationChannel(
            CHANNEL_EMERGENCY,
            "Emergency Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical health emergencies"
            setSound(
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                null
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(
                0,
                1000,
                500,
                1000
            )
        }

        // ============================================================
        // MEDICATION ESCALATION CHANNELS
        // ============================================================

        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val notificationAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        /**
         * T+0
         * Normal notification + vibration.
         */
        val doseReminderChannel = NotificationChannel(
            CHANNEL_DOSE_REMINDER,
            "Dose reminder (step 1)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(
                RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
                ),
                notificationAttrs
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(
                0,
                400,
                200,
                400
            )
        }

        /**
         * T+5 and T+10
         *
         * Silent because DoseAudioService owns:
         * - TTS at T+5
         * - alarm at T+10
         */
        val doseSilentChannel = NotificationChannel(
            CHANNEL_DOSE_SILENT,
            "Dose escalation (silent)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }

        /**
         * Fallback if Android refuses to start DoseAudioService.
         */
        val doseAlarmFallbackChannel = NotificationChannel(
            CHANNEL_DOSE_ALARM_FALLBACK,
            "Dose alarm (fallback)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                alarmAttrs
            )
            enableVibration(true)
        }

        /**
         * Persistent foreground-service notification.
         */
        val doseServiceChannel = NotificationChannel(
            CHANNEL_DOSE_SERVICE,
            "Medication alarm active",
            NotificationManager.IMPORTANCE_LOW
        )

        /**
         * Guardian-side missed-dose notification channel.
         */
        val guardianAlertChannel = NotificationChannel(
            CHANNEL_GUARDIAN_ALERT,
            "Guardian alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(
                RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
                ),
                notificationAttrs
            )
        }

        manager.createNotificationChannels(
            listOf(
                reminderChannel,
                emergencyChannel,
                doseReminderChannel,
                doseSilentChannel,
                doseAlarmFallbackChannel,
                doseServiceChannel,
                guardianAlertChannel
            )
        )
    }

    // ================================================================
    // EXISTING SMARTCARE METHODS
    // ================================================================

    /**
     * Existing normal medication reminder.
     * Kept unchanged for existing SmartCare functionality.
     */
    fun showReminder(
        title: String,
        message: String
    ) {
        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_REMINDER
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(
            NOTIFICATION_ID_REMINDER,
            notification
        )
    }

    /**
     * Existing emergency notification.
     * Used by the existing SOS/emergency flow.
     */
    fun showEmergencyAlarm(
        title: String,
        message: String
    ) {
        val fullScreenIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val fullScreenPendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_EMERGENCY
        )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(
                fullScreenPendingIntent,
                true
            )
            .setOngoing(true)
            .build()

        manager.notify(
            NOTIFICATION_ID_EMERGENCY,
            notification
        )
    }

    /**
     * Existing persistent notification.
     */
    fun buildPersistentNotification(
        title: String,
        message: String
    ): Notification {
        return NotificationCompat.Builder(
            context,
            CHANNEL_REMINDER
        )
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .build()
    }

    /**
     * Existing emergency cancellation.
     */
    fun cancelEmergency() {
        manager.cancel(
            NOTIFICATION_ID_EMERGENCY
        )
    }

    // ================================================================
    // MEDICATION ESCALATION
    // ================================================================

    /**
     * Opens the SmartCare app when the notification is tapped.
     *
     * Opening/dismissing a notification does NOT mark the medicine
     * as taken. Only the actual "Taken" action does that.
     */
    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            1,
            Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
        )

    /**
     * T+0
     *
     * Shows the first medication reminder.
     * Sound and vibration are controlled by the notification channel.
     */
    fun showDoseStep1(
        occId: String,
        title: String,
        text: String
    ) {
        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_DOSE_REMINDER
        )
            .setSmallIcon(
                android.R.drawable.ic_dialog_info
            )
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(
                NotificationCompat.CATEGORY_REMINDER
            )
            .setPriority(
                NotificationCompat.PRIORITY_HIGH
            )
            .setContentIntent(
                openAppIntent()
            )
            .setAutoCancel(true)
            .build()

        manager.notify(
            doseNotificationId(occId),
            notification
        )
    }

    /**
     * T+5 / T+10 visual notification.
     *
     * It is intentionally silent because DoseAudioService controls
     * TTS and alarm audio.
     */
    fun showDoseSilent(
        occId: String,
        title: String,
        text: String,
        urgent: Boolean
    ) {
        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_DOSE_SILENT
        )
            .setSmallIcon(
                if (urgent)
                    android.R.drawable.ic_dialog_alert
                else
                    android.R.drawable.ic_dialog_info
            )
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(
                if (urgent)
                    NotificationCompat.CATEGORY_ALARM
                else
                    NotificationCompat.CATEGORY_REMINDER
            )
            .setPriority(
                if (urgent)
                    NotificationCompat.PRIORITY_MAX
                else
                    NotificationCompat.PRIORITY_HIGH
            )
            .setContentIntent(
                openAppIntent()
            )
            .setOngoing(urgent)
            .setAutoCancel(!urgent)
            .setOnlyAlertOnce(true)
            .build()

        manager.notify(
            doseNotificationId(occId),
            notification
        )
    }

    /**
     * T+10 fallback.
     *
     * Used only if Android refuses to start the audio foreground
     * service. The notification becomes an insistent alarm.
     */
    fun showDoseAlarmFallback(
        occId: String,
        title: String,
        text: String
    ) {
        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_DOSE_ALARM_FALLBACK
        )
            .setSmallIcon(
                android.R.drawable.ic_dialog_alert
            )
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(
                NotificationCompat.CATEGORY_ALARM
            )
            .setPriority(
                NotificationCompat.PRIORITY_MAX
            )
            .setContentIntent(
                openAppIntent()
            )
            .setOngoing(true)
            .build()

        notification.flags =
            notification.flags or Notification.FLAG_INSISTENT

        manager.notify(
            doseNotificationId(occId),
            notification
        )
    }

    /**
     * Cancels the notification belonging to one medication occurrence.
     */
    fun cancelDose(
        occId: String
    ) {
        manager.cancel(
            doseNotificationId(occId)
        )
    }

    /**
     * Persistent notification required by DoseAudioService.
     */
    fun buildDoseServiceNotification(): Notification {
        return NotificationCompat.Builder(
            context,
            CHANNEL_DOSE_SERVICE
        )
            .setSmallIcon(
                android.R.drawable.ic_menu_today
            )
            .setContentTitle(
                "Medication reminder active"
            )
            .setContentText(
                "Open SmartCare and tap Take once you have taken your medicine."
            )
            .setOngoing(true)
            .setContentIntent(
                openAppIntent()
            )
            .build()
    }

    /**
     * Guardian-side missed-dose alert.
     *
     * IMPORTANT:
     * This only creates a notification on the device where this
     * method is called. The actual guardian phone notification
     * must be delivered through the existing FCM/backend flow.
     */
    fun showGuardianMissedAlert(
        title: String,
        body: String
    ) {
        val notification = NotificationCompat.Builder(
            context,
            CHANNEL_GUARDIAN_ALERT
        )
            .setSmallIcon(
                android.R.drawable.ic_dialog_alert
            )
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
            )
            .setPriority(
                NotificationCompat.PRIORITY_HIGH
            )
            .setContentIntent(
                openAppIntent()
            )
            .setAutoCancel(true)
            .build()

        manager.notify(
            (System.currentTimeMillis() and 0x3FFFFFFF).toInt(),
            notification
        )
    }
}