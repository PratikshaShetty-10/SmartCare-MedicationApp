package com.yourteam.smartcare.emergency.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import com.google.android.gms.location.LocationServices
import com.yourteam.smartcare.SmartCareApp
import com.yourteam.smartcare.api.GuardianNotifier
import com.yourteam.smartcare.emergency.ui.EmergencyPopupActivity
import com.yourteam.smartcare.utils.*
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable

@Serializable
data class GuardianRecord(
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
        val guardianId = intent?.getStringExtra("GUARDIAN_ID") ?: "DEFAULT"

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartCare::EscalationWakeLock").apply {
            acquire(15 * 60 * 1000L) // Holds for up to 15 minutes to fully cover the 10-min countdown window
        }

        val notificationHelper = NotificationHelper(this)
        val persistentNotif = notificationHelper.buildPersistentNotification("Escalation Active", "Monitoring adherence for $medName...")
        startForeground(1001, persistentNotif)

        scope.launch {
            runEscalationSequence(medName, guardianId)
        }

        return START_NOT_STICKY
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

        // Wait 1 minute
        delay(60_000)

        // ==========================================================
        // LEVEL 2: AFTER 1 MINUTE - REMINDER + VOICE MESSAGE
        // ==========================================================
        notificationHelper.showReminder("⏰ Reminder", "Please take your $medName.")
        vibrationHelper.vibratePattern(longArrayOf(0, 500, 200, 500))
        voiceHelper.speak("Please take your $medName.")

        // Wait another 1 minute (Total 2 minutes)
        delay(60_000)

        // ==========================================================
        // LEVEL 3: AFTER 2 MINUTES - INDEPENDENT ALARM + SUPABASE GUARDIAN + LIVE GPS
        // ==========================================================
        isEscalationActive = true
        notificationHelper.showEmergencyAlarm("🚨 EMERGENCY", "$medName is missed!")
        vibrationHelper.vibratePattern(longArrayOf(0, 1000, 500, 1000, 500, 1000), 0)

        // 1. Play Standalone Escalation Ringtone (Completely separate from SOS)
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

        // 3. Fetch Live GPS coordinates & Supabase Guardian Details, then notify
        withContext(Dispatchers.IO) {
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    val lat = location?.latitude ?: 0.0
                    val lng = location?.longitude ?: 0.0
                    val mapsLink = "https://maps.google.com/?q=$lat,$lng"

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Notify Guardian via Supabase / Notifier
                            guardianNotifier.notifyGuardian(
                                guardianId,
                                "🚨 EMERGENCY!",
                                "Patient has missed their scheduled dose of $medName! Live Location: $mapsLink"
                            )
                        } catch (err: Exception) {
                            err.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // ==========================================================
        // 10-MINUTE UNACKNOWLEDGED ESCALATION WINDOW
        // ==========================================================
        // If the patient fails to stop the alarm or take the medicine within 10 minutes (600,000 ms),
        // send a final missed notification to the guardian.
        delay(600_000)

        if (isEscalationActive) {
            withContext(Dispatchers.IO) {
                try {
                    guardianNotifier.notifyGuardian(
                        guardianId,
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