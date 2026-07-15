package com.yourteam.smartcare.emergency.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.yourteam.smartcare.R

class GpsTrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val channelId = "emergency_location_channel"

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must show notification immediately to satisfy Foreground Service requirements
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Emergency Tracking Active")
            .setContentText("Sharing your location with emergency services...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this exists
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        startForeground(1, notification)
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        // You would initialize your LocationRequest here
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Emergency Location", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}