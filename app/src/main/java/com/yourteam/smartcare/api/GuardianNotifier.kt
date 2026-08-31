package com.yourteam.smartcare.api

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
// WE FIXED THIS IMPORT to use the active connection
import com.yourteam.smartcare.data.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
data class AlertPayload(
    val token: String,
    val title: String,
    val body: String
)

// ADDED: The data class needed for the in-app Guardian banner
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
                set("device_token", token)
            }) { filter { eq("id", userId) } }
            Log.d(TAG, "✅ Token saved successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save token to DB", e)
        }
    }

    private suspend fun getGuardianToken(userId: String): String? {
        if (cachedToken != null) return cachedToken

        return try {
            val profile = SupabaseClient.client.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<Map<String, String>>()

            cachedToken = profile?.get("device_token")
            cachedToken
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch token from DB", e)
            null
        }
    }

    private suspend fun getGuardianPhoneNumber(userId: String): String? {
        if (cachedPhone != null) return cachedPhone

        return try {
            val profile = SupabaseClient.client.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<Map<String, String>>()

            cachedPhone = profile?.get("phone_number")
            cachedPhone
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch phone number from DB", e)
            null
        }
    }

    suspend fun notifyGuardian(guardianUserId: String, title: String, body: String): Boolean {
        val token = getGuardianToken(guardianUserId)

        if (token == null) {
            Log.e(TAG, "❌ Alert aborted: No push token found for user $guardianUserId")
            return false
        }

        return try {
            val payload = AlertPayload(token = token, title = title, body = body)

            SupabaseClient.client.functions.invoke(
                "send-alert",
                body = payload
            )

            Log.d(TAG, "✅ Notification successfully dispatched to Edge Function")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Edge Function execution failed", e)
            false
        }
    }

    private fun sendEmergencySms(phoneNumber: String, lat: Double, lng: Double, elderName: String = "Elder") {
        try {
            val mapLink = "https://maps.google.com/?q=$lat,$lng"
            val message = "EMERGENCY: $elderName pressed SOS! Location: $mapLink"

            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "✅ Emergency SMS successfully sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to send emergency SMS via SmsManager", e)
        }
    }

    suspend fun notifySOS(guardianUserId: String, lat: Double, lng: Double): Boolean {
        // 1. Send Push Notification
        val pushResult = notifyGuardian(
            guardianUserId,
            "🚨 SOS EMERGENCY!",
            "Help needed! Current location: https://maps.google.com/?q=$lat,$lng"
        )

        // 2. Send SMS
        val phoneNumber = getGuardianPhoneNumber(guardianUserId)
        if (!phoneNumber.isNullOrBlank()) {
            sendEmergencySms(phoneNumber, lat, lng)
        } else {
            Log.w(TAG, "⚠️ Phone number not found for guardian $guardianUserId, SMS skipped.")
        }

        // 3. NEW: Save to Database for the in-app Red Banner
        try {
            val alert = EmergencyAlert(
                guardian_id = guardianUserId,
                lat = lat,
                lng = lng,
                timestamp = Clock.System.now().toString()
            )
            SupabaseClient.client.from("emergency_alerts").insert(alert)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to log alert to DB for in-app banner", e)
        }

        return pushResult
    }
}