package com.yourteam.smartcare.notifications.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yourteam.smartcare.notifications.workers.StockCheckerWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {

            val workManager = WorkManager.getInstance(context)

            val networkConstraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            // Stock Checker
            //
            // Medication escalation is NOT scheduled here anymore.
            // It is handled by EscalationScheduler / DoseAlarmReceiver.
            val stockWork =
                PeriodicWorkRequestBuilder<StockCheckerWorker>(
                    12,
                    TimeUnit.HOURS
                )
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