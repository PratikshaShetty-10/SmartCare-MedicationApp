package com.yourteam.smartcare.notifications.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
// 1. Imported our custom supabase client
import com.yourteam.smartcare.utils.supabase
import com.yourteam.smartcare.emergency.services.EscalationManager
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class Medication(
    val id: String? = null,
    val elder_id: String,
    val med_name: String,
    val scheduled_time: String,
    val current_stock: Int
)

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ReminderWorker"
        const val KEY_ELDER_ID = "key_elder_id"
        const val KEY_GUARDIAN_ID = "key_guardian_id"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        // 1. Extract and validate inputs
        val elderId = inputData.getString(KEY_ELDER_ID)
        val guardianId = inputData.getString(KEY_GUARDIAN_ID)

        if (elderId.isNullOrBlank() || guardianId.isNullOrBlank()) {
            Log.e(TAG, "❌ ReminderWorker: Elder ID or Guardian ID is missing. Cannot fetch medications.")
            return@withContext Result.failure()
        }

        try {
            Log.d(TAG, "🚀 ReminderWorker: Fetching live medications for elder $elderId from Supabase...")

            // 2. Removed 'SmartCareApp.' prefix to use the newly imported client
            val userMeds = supabase.from("medications")
                .select {
                    filter { eq("elder_id", elderId) }
                }
                .decodeList<Medication>()

            // 3. Initialize manager using applicationContext to prevent memory leaks
            val escalationManager = EscalationManager(applicationContext)

            if (userMeds.isEmpty()) {
                Log.d(TAG, "⚠️ ReminderWorker: No medications found for elder $elderId")
                return@withContext Result.success()
            }

            // 4. Loop through userMeds to process scheduled alarms/reminders
            for (med in userMeds) {
                Log.d(TAG, "Processing medication: ${med.med_name} scheduled at ${med.scheduled_time}")

                // Trigger the escalation flow for each fetched medicine
                escalationManager.startEscalation(med.med_name, guardianId)
            }

            Log.d(TAG, "✅ ReminderWorker: Successfully processed ${userMeds.size} medications.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ ReminderWorker: Critical error during execution or Supabase query", e)

            // Return retry if it's a network/recoverable issue
            Result.retry()
        }
    }
}