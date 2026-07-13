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
            Log.d(TAG, "🚀 ReminderWorker started! Starting automatic escalation.")

            val escalationManager = EscalationManager(applicationContext)
            escalationManager.startEscalation("Medicine")  // Starts the 5min→10min timer

            Log.d(TAG, "✅ ReminderWorker finished. Escalation started.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ ReminderWorker failed", e)
            Result.retry()
        }
    }
}