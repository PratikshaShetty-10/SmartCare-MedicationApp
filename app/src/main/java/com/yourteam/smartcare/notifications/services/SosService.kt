package com.yourteam.smartcare.notifications.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.yourteam.smartcare.api.GuardianNotifier
import com.yourteam.smartcare.notifications.service.SosPopupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SosService : Service() {

    private lateinit var guardianNotifier: GuardianNotifier

    companion object {
        const val ACTION_STOP_SOS = "com.yourteam.smartcare.STOP_SOS"
        const val CHANNEL_ID = "sos_alert_channel"
        const val NOTIFICATION_ID = 999

        var isSosRunning = false
            private set

        var mediaPlayerInstance: MediaPlayer? = null

        fun stopSosAlarmInstance() {
            try {
                mediaPlayerInstance?.stop()
                mediaPlayerInstance?.release()
                mediaPlayerInstance = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isSosRunning = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        guardianNotifier = GuardianNotifier(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SOS) {
            stopSosAlarm()
            return START_NOT_STICKY
        }

        if (isSosRunning) {
            return START_STICKY
        }

        isSosRunning = true

        val notification = createSosNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        playSiren()
        dispatchEmergencyAlert()
        launchSosFullscreenPopup()

        return START_STICKY
    }

    private fun createSosNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergency SOS Active",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active SOS panic alarm and location dispatcher"
            }
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 EMERGENCY SOS ACTIVE")
            .setContentText("Siren is sounding and location is being sent to caregivers.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    private fun playSiren() {
        try {
            if (mediaPlayerInstance == null) {
                mediaPlayerInstance = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI).apply {
                    isLooping = true
                    setVolume(1.0f, 1.0f)
                    start()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchSosFullscreenPopup() {
        try {
            val popupIntent = Intent(applicationContext, SosPopupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            applicationContext.startActivity(popupIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dispatchEmergencyAlert() {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val lat = location?.latitude ?: 0.0
                val lng = location?.longitude ?: 0.0
                val mapsLink = "https://maps.google.com/?q=$lat,$lng"

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        guardianNotifier.notifyGuardian(
                            "default_guardian_id",
                            "🚨 PANIC SOS ALERT!",
                            "Patient has triggered the manual SOS panic button! Live Location: $mapsLink"
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopSosAlarm() {
        stopSosAlarmInstance()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopSosAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}