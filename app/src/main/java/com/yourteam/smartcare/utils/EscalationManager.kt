package com.yourteam.smartcare.utils

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper

class EscalationManager(private val context: Context) {

    private val guardianNotifier = GuardianNotifier(context)
    private val notificationHelper = NotificationHelper(context)
    private val vibrationHelper = VibrationHelper(context)
    private val voiceHelper = VoiceHelper(context)

    private val handler = Handler(Looper.getMainLooper())
    private var currentRingtone: Ringtone? = null
    private var isEscalating = false

    fun startEscalation(medName: String, guardianId: String) {
        stopAll()
        isEscalating = true

        level1(medName)
        handler.postDelayed({ if (isEscalating) level2(medName) }, 300_000) // 5 min
        handler.postDelayed({ if (isEscalating) level3(medName, guardianId) }, 600_000) // 10 min
    }

    fun level1(medName: String) {
        notificationHelper.showReminder("⏰ Reminder", "Time to take your $medName")
        vibrationHelper.vibrate(300)
    }

    fun level2(medName: String) {
        notificationHelper.showReminder("🔴 URGENT", "$medName is overdue!")
        voiceHelper.speak("Medicine! Medicine! Medicine!")
    }

    fun level3(medName: String, guardianId: String) {
        playAlarm()
        guardianNotifier.notifyGuardian(guardianId, "🚨 EMERGENCY!", "$medName missed!")
    }

    private fun playAlarm() {
        if (currentRingtone?.isPlaying == true) return
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        currentRingtone = RingtoneManager.getRingtone(context, alarmUri)
        currentRingtone?.play()
    }

    fun stopAll() {
        isEscalating = false
        handler.removeCallbacksAndMessages(null)
        currentRingtone?.takeIf { it.isPlaying }?.stop()
        vibrationHelper.cancel()
        voiceHelper.shutdown()
    }
}