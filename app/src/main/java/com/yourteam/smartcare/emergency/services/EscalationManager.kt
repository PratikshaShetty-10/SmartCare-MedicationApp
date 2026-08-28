package com.yourteam.smartcare.emergency.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.work.*
import com.yourteam.smartcare.api.GuardianNotifier
import com.yourteam.smartcare.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class EscalationManager(private val context: Context) {

    fun startEscalation(medName: String, guardianId: String) {
        val workData = workDataOf("MED_NAME" to medName, "GUARDIAN_ID" to guardianId)

        val request = OneTimeWorkRequestBuilder<EscalationWorker>()
            .setInputData(workData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "emergency_escalation",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun stopAll() {
        WorkManager.getInstance(context).cancelUniqueWork("emergency_escalation")
        NotificationHelper(context).cancelEmergency()
        VoiceHelper(context).shutdown()
        VibrationHelper(context).cancel()
    }

    class EscalationWorker(
        context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {

        private val notificationHelper = NotificationHelper(context)
        private val voiceHelper = VoiceHelper(applicationContext) // Use applicationContext for safety
        private val vibrationHelper = VibrationHelper(context)
        private val guardianNotifier = GuardianNotifier(context)
        private val mainHandler = Handler(Looper.getMainLooper())

        // Helper to safely trigger voice on the Main Thread from a background worker
        private fun speakOnMainThread(message: String) {
            mainHandler.post {
                voiceHelper.speak(message)
            }
        }

        override suspend fun doWork(): Result {
            val medName = inputData.getString("MED_NAME") ?: "Medication"
            val guardianId = inputData.getString("GUARDIAN_ID") ?: "DEFAULT"

            setForeground(ForegroundInfo(1001, notificationHelper.buildPersistentNotification("Escalation Active", "Reminding patient...")))

            // Level 1: Immediate
            notificationHelper.showReminder("⏰ Reminder", "Time to take your $medName")
            vibrationHelper.vibrate(600)
            speakOnMainThread("Time to take your $medName.")

            // Level 2: After 1 Minute
            delay(60_000)
            notificationHelper.showReminder("⏰ Reminder", "Please take your $medName.")
            vibrationHelper.vibratePattern(longArrayOf(0, 500, 200, 500))
            speakOnMainThread("Please take your $medName.")

            // Level 3: After another 1 Minute (Total 2 Minutes)
            delay(60_000)
            notificationHelper.showEmergencyAlarm("🚨 EMERGENCY", "$medName is missed!")
            vibrationHelper.vibratePattern(longArrayOf(0, 1000, 500, 1000, 500, 1000), 0)

            withContext(Dispatchers.IO) {
                guardianNotifier.notifyGuardian(guardianId, "🚨 EMERGENCY!", "$medName missed!")
            }

            return Result.success()
        }
    }
}