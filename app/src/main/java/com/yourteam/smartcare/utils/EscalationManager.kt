package com.yourteam.smartcare.utils

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

class EscalationManager(private val context: Context) {

    companion object {
        private const val TAG = "EscalationManager"
        private var currentRingtone: Ringtone? = null
        private var isEscalating = false
        private var handler: Handler? = null
        private var medicationName = "Medicine"
    }

    private val notificationHelper = NotificationHelper(context)
    private val vibrationHelper = VibrationHelper(context)
    private val voiceHelper = VoiceHelper(context)
    private val guardianNotifier = GuardianNotifier(context)

    /**
     * 🔥 START AUTOMATIC ESCALATION
     * Called by ReminderWorker when the scheduled time arrives
     */
    fun startEscalation(medName: String = "Medicine") {
        // Cancel any existing escalation
        stopAll()

        medicationName = medName
        isEscalating = true

        Log.d(TAG, "🚀 Starting automatic escalation for $medicationName")

        // 🟢 LEVEL 1: IMMEDIATE (0 minutes)
        level1()

        // 🟠 LEVEL 2: AFTER 5 MINUTES
        handler = Handler(Looper.getMainLooper())
        handler?.postDelayed({
            if (isEscalating) {
                level2()
            }
        }, 5 * 60 * 1000) // 5 minutes

        // 🔴 LEVEL 3: AFTER 10 MINUTES
        handler?.postDelayed({
            if (isEscalating) {
                level3()
            }
        }, 10 * 60 * 1000) // 10 minutes

        Log.d(TAG, "✅ Escalation scheduled: Level 2 in 5 min, Level 3 in 10 min")
    }

    private fun level1() {
        Log.d(TAG, "🟢 LEVEL 1: Notification (0 minutes)")
        notificationHelper.showReminder("⏰ Reminder", "Time to take your $medicationName")
        vibrationHelper.vibrate(300)
    }

    private fun level2() {
        Log.d(TAG, "🟠 LEVEL 2: Voice (+5 minutes)")
        notificationHelper.showReminder("🔴 URGENT", "$medicationName is overdue!")
        vibrationHelper.vibrate(1000)
        voiceHelper.speak("Medicine! Medicine! Medicine!")
    }

    private fun level3() {
        Log.d(TAG, "🔴 LEVEL 3: Emergency Alarm (+10 minutes)")
        playAlarm()
        notificationHelper.showEmergency("🚨 EMERGENCY", "$medicationName missed!")
        voiceHelper.speak("Emergency! Please take your medicine!")
        vibrationHelper.vibrate(2000)

        // 🔥 Guardian Alert - Dose Missed!
        guardianNotifier.notifyGuardian(
            title = "🚨 EMERGENCY! Dose Missed!",
            body = "Elder missed their $medicationName dose! Please check immediately!"
        )

        Log.w(TAG, "🔴 ALARM WILL NOT STOP UNTIL ELDER TAKES ACTION!")
    }

    private fun playAlarm() {
        if (currentRingtone?.isPlaying == true) return
        try {
            var alarmUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            currentRingtone = RingtoneManager.getRingtone(context, alarmUri)
            currentRingtone?.play()
            Log.d(TAG, "Alarm playing")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm", e)
        }
    }

    /**
     * ✅ Call when medicine is TAKEN + AI VERIFIED
     */
    fun stopAlarmWithVerification() {
        stopAll()
        guardianNotifier.notifyGuardian(
            title = "✅ Medicine Taken!",
            body = "Elder has taken $medicationName. Verification confirmed."
        )
        Log.d(TAG, "✅ Alarm stopped: Medicine verified!")
    }

    /**
     * ❌ Call when elder presses "Stop" WITHOUT scanning
     */
    fun stopAlarmWithoutVerification() {
        stopAll()
        guardianNotifier.notifyGuardian(
            title = "🚨 ALARM STOPPED WITHOUT VERIFICATION!",
            body = "Elder stopped the alarm but did NOT verify $medicationName. Please check immediately!"
        )
        Log.w(TAG, "⚠️ Alarm stopped WITHOUT verification!")
    }

    /**
     * Stop everything (Emergency stop)
     */
    fun stopAll() {
        isEscalating = false
        handler?.removeCallbacksAndMessages(null)
        handler = null

        currentRingtone?.let {
            if (it.isPlaying) {
                it.stop()
                Log.d(TAG, "Ringtone stopped")
            }
            currentRingtone = null
        }
        vibrationHelper.cancel()
        voiceHelper.shutdown()
        Log.d(TAG, "✅ All escalations stopped")
    }

    /**
     * Manual escalation (for testing buttons)
     */
    fun escalate(level: Int, medName: String = "Medicine") {
        Log.d(TAG, "Manual escalate Level $level")
        startEscalation(medName)
    }
}