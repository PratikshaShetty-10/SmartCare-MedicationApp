package com.yourteam.smartcare.utils

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log

class EscalationManager(private val context: Context) {

    companion object {
        private const val TAG = "EscalationManager"
        // 🔥 CRUCIAL FIX: Shared ringtone reference across ALL instances
        private var currentRingtone: Ringtone? = null
    }

    private val notificationHelper = NotificationHelper(context)
    private val vibrationHelper = VibrationHelper(context)
    private val voiceHelper = VoiceHelper(context)

    fun escalate(level: Int, medicationName: String = "Medicine") {
        Log.d(TAG, "Escalating to Level $level for $medicationName")

        when (level) {
            1 -> level1(medicationName)
            2 -> level2(medicationName)
            3 -> level3(medicationName)
            else -> Log.e(TAG, "Invalid escalation level: $level")
        }
    }

    private fun level1(medicationName: String) {
        notificationHelper.showReminder(
            "⏰ Reminder",
            "Time to take your $medicationName"
        )
        vibrationHelper.vibrate(300)
    }

    private fun level2(medicationName: String) {
        notificationHelper.showReminder(
            "🔴 URGENT",
            "$medicationName is overdue!"
        )
        vibrationHelper.vibrate(1000)
        voiceHelper.speak("Please take your $medicationName now.")
    }

    private fun level3(medicationName: String) {
        playAlarm()
        notificationHelper.showEmergency(
            "🚨 EMERGENCY",
            "$medicationName missed! Guardian alerted!"
        )
        voiceHelper.speak("Emergency! Guardian has been alerted.")
        vibrationHelper.vibrate(2000)

        Log.w(TAG, "LEVEL 3 EMERGENCY ACTIVATED for $medicationName")
    }

    private fun playAlarm() {
        // Don't start another alarm if one is already playing
        if (currentRingtone?.isPlaying == true) {
            return
        }

        try {
            var alarmUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            // Save to the SHARED companion variable
            currentRingtone = RingtoneManager.getRingtone(context, alarmUri)
            currentRingtone?.play()
            Log.d(TAG, "Alarm playing")

        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm", e)
        }
    }

    fun stopAll() {
        // 🔥 NOW THIS WORKS because currentRingtone is SHARED
        currentRingtone?.let {
            if (it.isPlaying) {
                it.stop()
                Log.d(TAG, "✅ Ringtone stopped")
            }
            currentRingtone = null
        }

        vibrationHelper.cancel()
        Log.d(TAG, "Vibration cancelled")

        voiceHelper.shutdown()
        Log.d(TAG, "Voice shut down")

        Log.d(TAG, "✅ All escalations stopped")
    }
}