package com.yourteam.smartcare

import android.app.Application
import com.yourteam.smartcare.escalation.ScheduleSync
import com.yourteam.smartcare.utils.NotificationHelper

class SmartCareApp : Application() {

    companion object {
        /**
         * Application context holder so ViewModel.markAsTaken()
         * can reach the escalation system without requiring
         * a Context parameter.
         */
        lateinit var instance: SmartCareApp
            private set
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        // Creates notification channels at application startup.
        NotificationHelper(this)

        // Keep medication schedule synchronized periodically.
        ScheduleSync.enqueuePeriodic(this)

        // Also request an immediate schedule synchronization
        // instead of waiting for the first periodic execution.
        ScheduleSync.enqueueNow(this)
    }
}