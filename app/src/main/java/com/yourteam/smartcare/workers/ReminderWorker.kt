package com.yourteam.smartcare.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log
import com.yourteam.smartcare.utils.EscalationManager

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ReminderWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "🚀 ReminderWorker started! Triggering medication reminder.")

            // Create the EscalationManager and start Level 1
            val escalationManager = EscalationManager(applicationContext)
            escalationManager.escalate(1, "Medicine")

            Log.d(TAG, "✅ ReminderWorker finished successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ ReminderWorker failed", e)
            Result.retry()
        }
    }
}