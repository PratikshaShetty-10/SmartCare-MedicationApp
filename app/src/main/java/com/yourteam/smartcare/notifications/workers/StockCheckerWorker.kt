package com.yourteam.smartcare.notifications.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yourteam.smartcare.repositories.MedicationRepository
import com.yourteam.smartcare.utils.NotificationHelper

class StockCheckerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val medicationRepository = MedicationRepository()
    private val notificationHelper = NotificationHelper(context)

    override suspend fun doWork(): Result {
        return try {
            val currentStock = medicationRepository.getRemainingStock()

            // Logic for 7-day and 3-day stock refill warnings
            if (currentStock == 7 || currentStock <= 3) {
                notificationHelper.showReminder(
                    "⚠️ Low Stock Refill Alert",
                    "Refill needed! Only $currentStock doses remaining."
                )
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}