package com.yourteam.smartcare.emergency.services

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.yourteam.smartcare.R
// 1. Imported our custom supabase client
import com.yourteam.smartcare.utils.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import io.github.jan.supabase.auth.auth

@Serializable
data class UserLocation(val user_id: String, val latitude: Double, val longitude: Double)

class GpsTrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val channelId = "emergency_location_channel"

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Added Android 14 safety check for location foreground services
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, buildNotification())
        }

        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    serviceScope.launch {
                        try {
                            // 2. Removed 'SmartCareApp.' prefix
                            val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: "anonymous_user"

                            // 3. Removed 'SmartCareApp.' prefix
                            supabase.from("user_locations").upsert(
                                UserLocation(userId, loc.latitude, loc.longitude)
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, channelId)
        .setContentTitle("Emergency Tracking Active")
        .setContentText("Sharing live location via SmartCare...")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setOngoing(true)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Emergency Location", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}