package com.yourteam.smartcare.api

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.yourteam.smartcare.SmartCareApp
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from

/**
 * Handles communication with Guardians via Push Notifications
 * via Supabase Edge Functions and Direct Cellular SMS.
 */
class GuardianNotifier(private val context: Context) {

    companion object {
        private const val TAG = "GuardianNotifier"
        private var cachedToken: String? = null
        private var cachedPhone: String? = null
    }

    suspend fun saveGuardianToken(userId: String, token: String) {
        cachedToken = token
        try {
            SmartCareApp.Companion.supabase.from("profiles").update({
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
            val profile = SmartCareApp.Companion.supabase.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<Map<String, Any>>()

            cachedToken = profile?.get("device_token") as? String
            cachedToken
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch token from DB", e)
            null
        }
    }

    /**
     * Fetches the guardian's phone number from Supabase profiles/relationships table.
     */
    private suspend fun getGuardianPhoneNumber(userId: String): String? {
        if (cachedPhone != null) return cachedPhone

        return try {
            val profile = SmartCareApp.Companion.supabase.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<Map<String, Any>>()

            // Adjust "phone_number" to match your actual database column name if needed
            cachedPhone = profile?.get("phone_number") as? String
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
            SmartCareApp.Companion.supabase.functions.invoke(
                "send-alert",
                body = mapOf("token" to token, "title" to title, "body" to body)
            )
            Log.d(TAG, "✅ Notification successfully dispatched to Edge Function")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Edge Function execution failed", e)
            false
        }
    }

    /**
     * Sends an automated emergency SMS using Android's SmsManager.
     */
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

    /**
     * OPTIMIZED: Triggers both Supabase Push Notification and Cellular SMS Fallback.
     */
    suspend fun notifySOS(guardianUserId: String, lat: Double, lng: Double): Boolean {
        // 1. Trigger Supabase Edge Function Push Notification
        val pushResult = notifyGuardian(
            guardianUserId,
            "🚨 SOS EMERGENCY!",
            "Help needed! Current location: https://maps.google.com/?q=$lat,$lng"
        )

        // 2. Fetch Guardian's phone number and trigger local SMS fallback
        val phoneNumber = getGuardianPhoneNumber(guardianUserId)
        if (!phoneNumber.isNullOrBlank()) {
            sendEmergencySms(phoneNumber, lat, lng)
        } else {
            Log.w(TAG, "⚠️ Phone number not found for guardian $guardianUserId, SMS skipped.")
        }

        return pushResult
    }
}