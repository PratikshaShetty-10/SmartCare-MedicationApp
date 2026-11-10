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
import io.github.jan.supabase.auth.auth

@Serializable
data class GuardianRecord(
    val phone: String?,
    val fcm_token: String?
)

@Serializable
data class RelationshipRecord(
    val guardian_id: String
)

@Serializable
data class MedicationRecord(
    val med_name: String?
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
        // Dynamically receives the medication row ID passed from the alarm/worker trigger
        val medicationId = intent?.getStringExtra("MEDICATION_ID") ?: ""


        // Automatically grabs the logged-in user's UUID from the active session safely
        val elderId = supabase.auth.currentSessionOrNull()?.user?.id ?: ""

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartCare::EscalationWakeLock").apply {
            acquire(15 * 60 * 1000L) // Holds for up to 15 minutes
        }

        val notificationHelper = NotificationHelper(this)
        val persistentNotif = notificationHelper.buildPersistentNotification("Escalation Active", "Monitoring adherence...")

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
            // Dynamically query the medication name from Supabase using the row ID
            val medName = fetchMedicationNameFromSupabase(medicationId) ?: "Scheduled Medication"
            runEscalationSequence(medName, elderId)
        }

        return START_NOT_STICKY
    }

    private suspend fun fetchMedicationNameFromSupabase(medicationId: String): String? {
        if (medicationId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val result = supabase
                    .from("medications")
                    .select(Columns.list("med_name") ) {
                        filter {
                            eq("id", medicationId)
                        }
                    }
                    .decodeSingleOrNull<MedicationRecord>()
                result?.med_name
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun fetchGuardianFromSupabase(elderId: String): GuardianRecord? {
        if (elderId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Find the guardian_id linked to this elder in the relationships table
                val relationshipResult = supabase
                    .from("relationships")
                    .select(Columns.list("guardian_id")) {
                        filter {
                            eq("elder_id", elderId)
                        }
                    }
                    .decodeSingleOrNull<RelationshipRecord>()

                val guardianId = relationshipResult?.guardian_id ?: return@withContext null

                // Step 2: Fetch the guardian's phone and fcm_token from profiles using their guardian_id
                val profileResult = supabase
                    .from("profiles")
                    .select(Columns.list("phone", "fcm_token")) {
                        filter {
                            eq("id", guardianId)
                        }
                    }
                    .decodeSingleOrNull<GuardianRecord>()

                profileResult
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private suspend fun runEscalationSequence(medName: String, elderId: String) {
        val notificationHelper = NotificationHelper(applicationContext)
        val voiceHelper = VoiceHelper(applicationContext)
        val vibrationHelper = VibrationHelper(applicationContext)
        val guardianNotifier = GuardianNotifier(applicationContext)

        delay(1000)

        // ==========================================================
        // LEVEL 1: IMMEDIATE (0 MINUTES) - NOTIFICATION + VIBRATION
        // ==========================================================
        notificationHelper.showReminder("⏰ Medication Reminder", "Time to take your $medName")
        vibrationHelper.vibrate(600)

        // TESTING DELAY: 2 minutes -> 120_000L (Change to 300_000L for production)
        delay(120_000L)

        // ==========================================================
        // LEVEL 2: AFTER 2 MINUTES - REMINDER + VOICE MESSAGE
        // ==========================================================
        notificationHelper.showReminder("⏰ Reminder", "Please take your $medName.")
        vibrationHelper.vibratePattern(longArrayOf(0, 500, 200, 500))
        voiceHelper.speak("Please take your $medName.")

        // TESTING DELAY: 2 minutes -> 120_000L (Change to 300_000L for production)
        delay(120_000L)

        // ==========================================================
        // LEVEL 3: AFTER 4 MINUTES TOTAL - ALARM + DATABASE GUARDIAN + GPS
        // ==========================================================
        isEscalationActive = true
        notificationHelper.showEmergencyAlarm("🚨 EMERGENCY", "$medName is missed!")
        vibrationHelper.vibratePattern(longArrayOf(0, 1000, 500, 1000, 500, 1000), 0)

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

        val popupIntent = Intent(applicationContext, EmergencyPopupActivity::class.java).apply {
            putExtra("MED_NAME", medName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        applicationContext.startActivity(popupIntent)

        withContext(Dispatchers.IO) {
            try {
                val guardianRecord = fetchGuardianFromSupabase(elderId)
                val targetPhone = guardianRecord?.phone ?: ""

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
                                if (targetPhone.isNotBlank()) {
                                    guardianNotifier.notifyGuardian(
                                        targetPhone,
                                        "🚨 EMERGENCY!",
                                        "Patient has missed their scheduled dose of $medName! Live Location: $mapsLink"
                                    )
                                }
                            } catch (err: Exception) {
                                err.printStackTrace()
                            }
                        }
                    }
                } else {
                    if (targetPhone.isNotBlank()) {
                        guardianNotifier.notifyGuardian(
                            targetPhone,
                            "🚨 EMERGENCY!",
                            "Patient has missed their scheduled dose of $medName! (Location permission unavailable)"
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // ==========================================================
        // FINAL UNACKNOWLEDGED ESCALATION WINDOW (2 mins)
        // ==========================================================
        delay(120_000L)

        if (isEscalationActive) {
            withContext(Dispatchers.IO) {
                try {
                    val guardianRecord = fetchGuardianFromSupabase(elderId)
                    val targetPhone = guardianRecord?.phone ?: ""

                    if (targetPhone.isNotBlank()) {
                        guardianNotifier.notifyGuardian(
                            targetPhone,
                            "⚠️ CRITICAL UNATTENDED ALERT!",
                            "Patient has STILL NOT taken $medName after continuous emergency sounding!"
                        )
                    }
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