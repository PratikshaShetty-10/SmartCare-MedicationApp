package com.yourteam.smartcare.api

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.AdherenceLog
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.data.model.Relationship
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class AlertPayload(
    val token: String,
    val title: String,
    val body: String
)

@Serializable
data class EmergencyAlert(
    val guardian_id: String,
    val lat: Double,
    val lng: Double,
    val timestamp: String,
    val status: String = "ACTIVE"
)

class GuardianNotifier(private val context: Context) {

    companion object {
        private const val TAG = "GuardianNotifier"
        private var cachedToken: String? = null
        private var cachedPhone: String? = null
    }

    suspend fun saveGuardianToken(userId: String, token: String) {
        cachedToken = token
        try {
            SupabaseClient.client.from("profiles").update({
                set("fcm_token", token)
            }) {
                filter {
                    eq("id", userId)
                }
            }

            Log.d(TAG, "✅ Token saved successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save token to DB", e)
        }
    }

    private suspend fun getGuardianToken(userId: String): String? {
        if (cachedToken != null) return cachedToken

        return try {
            val profile =
                SupabaseClient.client
                    .from("profiles")
                    .select {
                        filter {
                            eq("id", userId)
                        }
                    }
                    .decodeSingleOrNull<Map<String, String?>>()

            cachedToken = profile?.get("fcm_token")
            cachedToken
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch token from DB", e)
            null
        }
    }

    private suspend fun getGuardianPhoneNumber(userId: String): String? {
        if (cachedPhone != null) return cachedPhone

        return try {
            val profile =
                SupabaseClient.client
                    .from("profiles")
                    .select {
                        filter {
                            eq("id", userId)
                        }
                    }
                    .decodeSingleOrNull<Map<String, String?>>()

            cachedPhone = profile?.get("phone")
            cachedPhone
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch phone number from DB", e)
            null
        }
    }

    suspend fun notifyGuardian(
        guardianUserId: String,
        title: String,
        body: String
    ): Boolean {

        val token = getGuardianToken(guardianUserId)

        if (token == null) {
            Log.e(
                TAG,
                "❌ Alert aborted: No push token found for user $guardianUserId"
            )
            return false
        }

        return try {
            val payload =
                AlertPayload(
                    token = token,
                    title = title,
                    body = body
                )

            SupabaseClient.client.functions.invoke(
                "send-alert",
                body = payload
            )

            Log.d(
                TAG,
                "✅ Notification successfully dispatched to Edge Function"
            )

            true

        } catch (e: Exception) {
            Log.e(
                TAG,
                "❌ Edge Function execution failed",
                e
            )

            false
        }
    }

    private fun sendEmergencySms(
        phoneNumber: String,
        lat: Double,
        lng: Double,
        elderName: String = "Elder"
    ) {
        try {
            val mapLink =
                "https://maps.google.com/?q=$lat,$lng"

            // KEEP THIS SMS WORDING UNCHANGED
            val message =
                "EMERGENCY: $elderName pressed SOS! Location: $mapLink"

            val smsManager =
                if (
                    android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.S
                ) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

            smsManager.sendTextMessage(
                phoneNumber,
                null,
                message,
                null,
                null
            )

            Log.d(
                TAG,
                "✅ Emergency SMS successfully sent to $phoneNumber"
            )

        } catch (e: Exception) {
            Log.e(
                TAG,
                "❌ Failed to send emergency SMS via SmsManager",
                e
            )
        }
    }

    /*
     * Builds today's adherence summary using the same rules
     * as the Guardian P7 Adherence Report.
     *
     * This is READ-ONLY.
     *
     * It does not insert, update, or delete adherence records.
     */
    private suspend fun getTodayAdherenceSummary(
        guardianUserId: String
    ): String {

        return try {

            val relationship =
                SupabaseClient.client
                    .from("relationships")
                    .select {
                        filter {
                            eq("guardian_id", guardianUserId)
                        }
                    }
                    .decodeSingleOrNull<Relationship>()

            if (relationship == null) {
                Log.w(
                    TAG,
                    "⚠️ No Elder relationship found for Guardian $guardianUserId"
                )

                return "Adherence report unavailable."
            }

            val elderId = relationship.elderId

            val medications =
                SupabaseClient.client
                    .from("medications")
                    .select {
                        filter {
                            eq("elder_id", elderId)
                        }
                    }
                    .decodeList<Medication>()

            if (medications.isEmpty()) {
                return "No medications scheduled for today."
            }

            val medicationIds =
                medications.mapNotNull { it.id }

            if (medicationIds.isEmpty()) {
                return "No medications scheduled for today."
            }

            val logs =
                SupabaseClient.client
                    .from("adherence_logs")
                    .select {
                        filter {
                            isIn(
                                "medication_id",
                                medicationIds
                            )
                        }
                    }
                    .decodeList<AdherenceLog>()

            val today =
                LocalDate.now()

            val zone =
                ZoneId.systemDefault()

            /*
             * Same date handling as P7:
             * Supabase scheduled_at → device timezone → LocalDate.
             */
            val logsForToday =
                logs.filter { log ->

                    try {
                        OffsetDateTime
                            .parse(log.scheduledAt)
                            .toInstant()
                            .atZone(zone)
                            .toLocalDate() == today

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Failed to parse adherence scheduledAt=${log.scheduledAt}",
                            e
                        )

                        false
                    }
                }

            /*
             * Same historical/creation rule as P7.
             * For today's SOS report, don't show a medication
             * if it was created after today's scheduled occurrence.
             */
            val validMedications =
                medications.filter { medication ->

                    try {

                        val scheduledLocalTime =
                            LocalTime.parse(
                                medication.scheduledTime
                            )

                        val scheduledOccurrence =
                            today
                                .atTime(scheduledLocalTime)
                                .atZone(zone)
                                .toInstant()

                        val createdAt =
                            medication.createdAt

                        if (createdAt == null) {
                            true
                        } else {

                            val createdInstant =
                                OffsetDateTime
                                    .parse(createdAt)
                                    .toInstant()

                            !createdInstant.isAfter(
                                scheduledOccurrence
                            )
                        }

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Failed today's medication check for " +
                                    "${medication.medName}",
                            e
                        )

                        // Same fallback as P7:
                        // keep the medication if evaluation fails.
                        true
                    }
                }

            var takenCount = 0
            var missedCount = 0
            var pendingCount = 0

            val reportLines =
                validMedications
                    .sortedBy {
                        runCatching {
                            LocalTime.parse(
                                it.scheduledTime
                            )
                        }.getOrElse {
                            LocalTime.MAX
                        }
                    }
                    .map { medication ->

                        val medicationLogs =
                            logsForToday.filter {
                                it.medicationId == medication.id
                            }

                        /*
                         * Same P7 rule:
                         * only taken/missed are normal adherence statuses.
                         */
                        val log =
                            medicationLogs.lastOrNull {
                                it.status == "taken" ||
                                        it.status == "missed"
                            }

                        when {
                            log == null -> {

                                pendingCount++

                                "⏳ ${medication.medName} — Pending"
                            }

                            log.status == "taken" -> {

                                takenCount++

                                val takenAt =
                                    log.takenAt?.let {
                                        formatLocalTime(it)
                                    }

                                if (takenAt != null) {
                                    "✅ ${medication.medName} — Taken at $takenAt"
                                } else {
                                    "✅ ${medication.medName} — Taken"
                                }
                            }

                            else -> {

                                missedCount++

                                "❌ ${medication.medName} — Missed"
                            }
                        }
                    }

            if (reportLines.isEmpty()) {
                return "No medications scheduled for today."
            }

            buildString {

                appendLine("Today's medication adherence:")
                appendLine()

                reportLines.forEach { line ->
                    appendLine(line)
                }

                appendLine()
                append(
                    "Taken: $takenCount | " +
                            "Missed: $missedCount | " +
                            "Pending: $pendingCount"
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "❌ Failed to build SOS adherence report",
                e
            )

            /*
             * IMPORTANT:
             * Adherence failure must never stop the SOS alert.
             */
            "Adherence report unavailable."
        }
    }

    /*
     * Converts Supabase timestamp to the device's local time.
     *
     * Same approach used by P7 AdherenceReportScreen.
     */
    private fun formatLocalTime(
        timestamp: String
    ): String? {

        return runCatching {

            OffsetDateTime
                .parse(timestamp)
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .format(
                    DateTimeFormatter.ofPattern(
                        "h:mm a",
                        Locale.getDefault()
                    )
                )

        }.getOrNull()
    }

    suspend fun notifySOS(
        guardianUserId: String,
        lat: Double,
        lng: Double
    ): Boolean {

        /*
         * Build today's adherence report first.
         *
         * If this fails, getTodayAdherenceSummary()
         * returns a safe fallback and SOS continues.
         */
        val adherenceSummary =
            getTodayAdherenceSummary(
                guardianUserId
            )

        // 1. Send Push Notification
        val pushResult =
            notifyGuardian(
                guardianUserId,
                "🚨 SOS EMERGENCY!",
                buildString {

                    appendLine(
                        "Help needed! Elder has pressed SOS."
                    )

                    appendLine()

                    appendLine(
                        "Current location:"
                    )

                    appendLine(
                        "https://maps.google.com/?q=$lat,$lng"
                    )

                    appendLine()

                    append(adherenceSummary)
                }
            )

        // 2. Send SMS
        //
        // IMPORTANT:
        // SMS wording is intentionally unchanged.
        val phoneNumber =
            getGuardianPhoneNumber(
                guardianUserId
            )

        if (!phoneNumber.isNullOrBlank()) {

            sendEmergencySms(
                phoneNumber,
                lat,
                lng
            )

        } else {

            Log.w(
                TAG,
                "⚠️ Phone number not found for guardian " +
                        "$guardianUserId, SMS skipped."
            )
        }

        // 3. Save to Database for the in-app Red Banner
        try {

            val alert =
                EmergencyAlert(
                    guardian_id = guardianUserId,
                    lat = lat,
                    lng = lng,
                    timestamp =
                        Clock.System.now().toString()
                )

            SupabaseClient.client
                .from("emergency_alerts")
                .insert(alert)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "❌ Failed to log alert to DB for in-app banner",
                e
            )
        }

        return pushResult
    }
}