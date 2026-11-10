package com.yourteam.smartcare.emergency.services.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.content.Intent
import android.os.Build
import com.yourteam.smartcare.emergency.services.EscalationForegroundService

class MedicationReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    private val context = appContext

    override fun doWork(): Result {
        // Pass the database row ID dynamically scheduled by the guardian
        val medicationId = inputData.getString("MEDICATION_ID") ?: ""

        val serviceIntent = Intent(context, EscalationForegroundService::class.java).apply {
            putExtra("MEDICATION_ID", medicationId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        return Result.success()
    }
}