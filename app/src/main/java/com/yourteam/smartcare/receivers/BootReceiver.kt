package com.yourteam.smartcare.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yourteam.smartcare.workers.ReminderWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule the daily reminder when the phone turns on
            val reminderWork = PeriodicWorkRequestBuilder<ReminderWorker>(
                24, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "smartcare_reminder",
                ExistingPeriodicWorkPolicy.KEEP,
                reminderWork
            )
        }
    }
}