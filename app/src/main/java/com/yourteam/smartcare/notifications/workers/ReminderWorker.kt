package com.yourteam.smartcare.notifications.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yourteam.smartcare.emergency.services.EscalationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ReminderWorker"
        const val KEY_MED_NAME = "key_med_name"
        const val KEY_GUARDIAN_ID = "key_guardian_id"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        // 1. Extract and validate inputs
        val medName = inputData.getString(KEY_MED_NAME) ?: "Medicine"
        val guardianId = inputData.getString(KEY_GUARDIAN_ID)

        if (guardianId.isNullOrBlank()) {
            Log.e(TAG, "❌ ReminderWorker: Guardian ID is missing. Cannot trigger escalation.")
            return@withContext Result.failure()
        }

        try {
            Log.d(TAG, "🚀 ReminderWorker: Starting escalation for $medName (Guardian: $guardianId)")

            // 2. Initialize manager using applicationContext to prevent memory leaks
            val escalationManager = EscalationManager(applicationContext)

            // 3. Trigger the escalation flow
            // Using withContext(Dispatchers.Main) if internal logic touches UI components/Looper
            escalationManager.startEscalation(medName, guardianId)

            Log.d(TAG, "✅ ReminderWorker: Successfully triggered escalation for $medName")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ ReminderWorker: Critical error during execution", e)

            // Return retry if it's a recoverable issue, otherwise failure
            Result.retry()
        }
    }
}