package com.yourteam.smartcare.emergency.services

import android.content.Context
import androidx.work.*
import com.yourteam.smartcare.api.GuardianNotifier
import com.yourteam.smartcare.utils.*
import kotlinx.coroutines.delay

class EscalationManager(private val context: Context) {

    // --- TRIGGER ---
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
    }

    // --- SURVIVAL WORKER ---
    class EscalationWorker(
        context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {

        private val notificationHelper = NotificationHelper(context)
        private val voiceHelper = VoiceHelper(context)
        private val guardianNotifier = GuardianNotifier(context)

        override suspend fun doWork(): Result {
            val medName = inputData.getString("MED_NAME") ?: "Medication"
            val guardianId = inputData.getString("GUARDIAN_ID") ?: "DEFAULT"

            // Foreground service promotion to survive system kills
            setForeground(ForegroundInfo(1001, notificationHelper.buildPersistentNotification("Escalation Active", "Reminding patient...")))

            // Level 1: Immediate
            notificationHelper.showReminder("⏰ Reminder", "Time to take your $medName")

            // Level 2: 5 Minutes
            delay(300_000)
            notificationHelper.showReminder("⏰ Reminder", "Please take your $medName.")
            voiceHelper.speak("Please take your $medName.")

            // Level 3: 10 Minutes
            delay(300_000)
            notificationHelper.showEmergencyAlarm("🚨 EMERGENCY", "$medName is missed!")
            guardianNotifier.notifyGuardian(guardianId, "🚨 EMERGENCY!", "$medName missed!")

            return Result.success()
        }
    }
}