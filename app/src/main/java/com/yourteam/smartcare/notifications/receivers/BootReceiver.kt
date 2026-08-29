package com.yourteam.smartcare.notifications.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
// UPDATE THESE IMPORTS:
import com.yourteam.smartcare.notifications.workers.ReminderWorker
import com.yourteam.smartcare.notifications.workers.StockCheckerWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            val workManager = WorkManager.getInstance(context)

            val networkConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 1. Medication Reminders
            val reminderWork = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "smartcare_reminder",
                ExistingPeriodicWorkPolicy.UPDATE,
                reminderWork
            )

            // 2. Stock Checker
            val stockWork = PeriodicWorkRequestBuilder<StockCheckerWorker>(12, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "smartcare_stock_check",
                ExistingPeriodicWorkPolicy.UPDATE,
                stockWork
            )
        }
    }
}