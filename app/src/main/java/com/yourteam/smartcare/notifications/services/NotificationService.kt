package com.yourteam.smartcare.notifications.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.yourteam.smartcare.utils.NotificationHelper

class NotificationService : Service() {
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // You can use this service to perform batch checks on reminders
        // or to clean up notification state if the app was force-closed.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}