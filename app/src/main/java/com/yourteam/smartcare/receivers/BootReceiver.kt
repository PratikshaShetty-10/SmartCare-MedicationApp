package com.yourteam.smartcare.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yourteam.smartcare.workers.ReminderWorker
import com.yourteam.smartcare.workers.StockCheckerWorker // Ensure this is imported
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val workManager = WorkManager.getInstance(context)

            // 1. Re-schedule Medication Reminders
            val reminderWork = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(
                "smartcare_reminder",
                ExistingPeriodicWorkPolicy.UPDATE,
                reminderWork
            )

            // 2. Re-schedule Stock Checker (Vital for your Backend role)
            val stockWork = PeriodicWorkRequestBuilder<StockCheckerWorker>(12, TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(
                "smartcare_stock_check",
                ExistingPeriodicWorkPolicy.UPDATE,
                stockWork
            )
        }
    }
}