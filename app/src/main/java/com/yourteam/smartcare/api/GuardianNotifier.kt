package com.yourteam.smartcare.api

import android.content.Context
import android.util.Log
import com.yourteam.smartcare.SmartCareApp
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from

/**
 * Handles communication with Guardians via Push Notifications
 * via Supabase Edge Functions.
 */
class GuardianNotifier(private val context: Context) {

    companion object {
        private const val TAG = "GuardianNotifier"
        private var cachedToken: String? = null
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
     * OPTIMIZED: Now returns Boolean so the ViewModel can react to success/failure.
     */
    suspend fun notifySOS(guardianUserId: String, lat: Double, lng: Double): Boolean {
        val mapLink = "https://maps.google.com/?q=$lat,$lng"
        return notifyGuardian(
            guardianUserId,
            "🚨 SOS EMERGENCY!",
            "Help needed! Current location: $mapLink"
        )
    }
}