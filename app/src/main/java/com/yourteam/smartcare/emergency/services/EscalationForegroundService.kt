package com.yourteam.smartcare.emergency.services

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.yourteam.smartcare.api.GuardianNotifier
import com.yourteam.smartcare.emergency.ui.EmergencyPopupActivity
import com.yourteam.smartcare.utils.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import com.yourteam.smartcare.utils.supabase

@Serializable
data class GuardianRecord(
    val id: String,
    val guardian_phone: String?,
    val fcm_token: String?
)

class EscalationForegroundService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        var mediaPlayerInstance: MediaPlayer? = null
        var isEscalationActive = false

        fun stopEscalationAlarm() {
            try {
                mediaPlayerInstance?.stop()
                mediaPlayerInstance?.release()
                mediaPlayerInstance = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isEscalationActive = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val medName = intent?.getStringExtra("MED_NAME") ?: "Medication"
        val guardianId = intent?.getStringExtra("GUARDIAN_ID") ?: ""

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartCare::EscalationWakeLock").apply {
            acquire(15 * 60 * 1000L) // Holds for up to 15 minutes
        }

        val notificationHelper = NotificationHelper(this)
        val persistentNotif = notificationHelper.buildPersistentNotification("Escalation Active", "Monitoring adherence for $medName...")

        // Safely starts the foreground service for Android 14+ devices
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                1001,
                persistentNotif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(1001, persistentNotif)
        }

        scope.launch {
            runEscalationSequence(medName, guardianId)
        }

        return START_NOT_STICKY
    }

    private suspend fun fetchGuardianFromSupabase(guardianId: String): GuardianRecord? {
        if (guardianId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                // Fixed the Supabase reference to use the imported utils client
                val result = supabase
                    .from("guardians")
                    .select(Columns.list("id", "guardian_phone", "fcm_token")) {
                        filter {
                            eq("id", guardianId)
                        }
                    }
                    .decodeSingleOrNull<GuardianRecord>()
                result
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun runEscalationSequence(medName: String, guardianId: String) {
        val notificationHelper = NotificationHelper(applicationContext)
        val voiceHelper = VoiceHelper(applicationContext)
        val vibrationHelper = VibrationHelper(applicationContext)
        val guardianNotifier = GuardianNotifier(applicationContext)

        delay(1000)

        // ==========================================================
        // LEVEL 1: IMMEDIATE (0 MINUTES) - SILENT NOTIFICATION + VIBRATION ONLY
        // ==========================================================
        notificationHelper.showReminder("⏰ Medication Reminder", "Time to take your $medName")
        vibrationHelper.vibrate(600)

        // Wait 5 minutes
        delay(300_000)

        // ==========================================================
        // LEVEL 2: AFTER 5 MINUTES - REMINDER + VOICE MESSAGE
        // ==========================================================
        notificationHelper.showReminder("⏰ Reminder", "Please take your $medName.")
        vibrationHelper.vibratePattern(longArrayOf(0, 500, 200, 500))
        voiceHelper.speak("Please take your $medName.")

        // Wait another 5 minutes (Total 10 minutes)
        delay(300_000)

        // ==========================================================
        // LEVEL 3: AFTER 10 MINUTES - INDEPENDENT ALARM + SUPABASE GUARDIAN + LIVE GPS
        // ==========================================================
        isEscalationActive = true
        notificationHelper.showEmergencyAlarm("🚨 EMERGENCY", "$medName is missed!")
        vibrationHelper.vibratePattern(longArrayOf(0, 1000, 500, 1000, 500, 1000), 0)

        // 1. Play Standalone Escalation Ringtone
        try {
            if (mediaPlayerInstance == null) {
                mediaPlayerInstance = MediaPlayer.create(applicationContext, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI).apply {
                    isLooping = true
                    setVolume(1.0f, 1.0f)
                    start()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Launch Full-Screen Emergency Popup Activity over lock screen
        val popupIntent = Intent(applicationContext, EmergencyPopupActivity::class.java).apply {
            putExtra("MED_NAME", medName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        applicationContext.startActivity(popupIntent)

        // 3. Fetch Guardian details from Supabase & Live GPS coordinates, then notify
        withContext(Dispatchers.IO) {
            try {
                val guardianRecord = fetchGuardianFromSupabase(guardianId)
                val targetPhone = guardianRecord?.guardian_phone ?: guardianId

                val hasFineLocation = ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                val hasCoarseLocation = ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasFineLocation || hasCoarseLocation) {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        val lat = location?.latitude ?: 0.0
                        val lng = location?.longitude ?: 0.0
                        val mapsLink = "https://maps.google.com/?q=$lat,$lng"

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                guardianNotifier.notifyGuardian(
                                    targetPhone,
                                    "🚨 EMERGENCY!",
                                    "Patient has missed their scheduled dose of $medName! Live Location: $mapsLink"
                                )
                            } catch (err: Exception) {
                                err.printStackTrace()
                            }
                        }
                    }
                } else {
                    guardianNotifier.notifyGuardian(
                        targetPhone,
                        "🚨 EMERGENCY!",
                        "Patient has missed their scheduled dose of $medName! (Location permission unavailable)"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // ==========================================================
        // 10-MINUTE UNACKNOWLEDGED ESCALATION WINDOW
        // ==========================================================
        delay(600_000)

        if (isEscalationActive) {
            withContext(Dispatchers.IO) {
                try {
                    val guardianRecord = fetchGuardianFromSupabase(guardianId)
                    val targetPhone = guardianRecord?.guardian_phone ?: guardianId

                    guardianNotifier.notifyGuardian(
                        targetPhone,
                        "⚠️ CRITICAL UNATTENDED ALERT!",
                        "Patient has STILL NOT taken $medName after 10 minutes of continuous emergency sounding!"
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEscalationAlarm()
        job.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}