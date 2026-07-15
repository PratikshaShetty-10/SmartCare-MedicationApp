package com.yourteam.smartcare.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yourteam.smartcare.SmartCareApp
import com.yourteam.smartcare.utils.NotificationHelper
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StockCheckerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "StockCheckerWorker"
        const val KEY_ELDER_ID = "key_elder_id"
        private const val DAYS_TO_WARN = 7
        private const val DAYS_TO_URGENT = 3
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val elderId = inputData.getString(KEY_ELDER_ID)

        if (elderId == null) {
            Log.e(TAG, "❌ StockCheckerWorker: Missing elder_id in inputData")
            return@withContext Result.failure()
        }

        try {
            Log.d(TAG, "📦 Checking stock for elder: $elderId")

            // 1. Fetch data from Supabase
            val response = SmartCareApp.supabase.from("medications").select {
                filter { eq("elder_id", elderId) }
            }

            val notificationHelper = NotificationHelper(applicationContext)

            // 2. Safely decode the list
            val medications = response.decodeList<Map<String, Any>>()
            Log.d(TAG, "📦 Found ${medications.size} medications to check.")

            // 3. Process each medication with safe casting
            medications.forEach { med ->
                val name = med["med_name"]?.toString() ?: "Unknown Medicine"

                // Safe parsing: convert Number to Double then to Int to avoid ClassCastException
                val currentStock = (med["current_stock"] as? Number)?.toDouble()?.toInt() ?: 0
                val dailyDosage = (med["daily_dosage"] as? Number)?.toDouble()?.toInt() ?: 1

                val daysRemaining = if (dailyDosage > 0) currentStock / dailyDosage else 0

                Log.d(TAG, "Checking $name: Stock=$currentStock, Dosage=$dailyDosage, DaysLeft=$daysRemaining")
                processStockAlert(name, currentStock, daysRemaining, notificationHelper)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ StockCheckerWorker: Critical error during stock check", e)
            // Retry allows the worker to try again if the error was due to network connectivity
            Result.retry()
        }
    }

    private fun processStockAlert(name: String, stock: Int, days: Int, notifier: NotificationHelper) {
        when {
            days <= 0 -> {
                notifier.showReminder("🚨 OUT OF STOCK!", "$name is empty!")
            }
            days <= DAYS_TO_URGENT -> {
                notifier.showReminder("🚨 URGENT: Low Stock!", "$name has $days days left.")
            }
            days <= DAYS_TO_WARN -> {
                notifier.showReminder("⚠️ Stock Warning", "$name will run out in $days days.")
            }
        }
    }
}