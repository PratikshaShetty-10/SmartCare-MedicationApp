package com.yourteam.smartcare.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.yourteam.smartcare.SmartCareApp
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.*

class GuardianNotifier(private val context: Context) {

    companion object {
        private const val TAG = "GuardianNotifier"
        private var cachedToken: String? = null
    }

    // Call this ONLY when the Guardian logs in
    fun saveGuardianToken(userId: String, token: String) {
        cachedToken = token
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SmartCareApp.supabase.from("profiles").update({
                    set("device_token", token)
                }) { filter { eq("id", userId) } }
                Log.d(TAG, "✅ Token saved to Supabase")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to save token", e)
            }
        }
    }

    // Robust fetcher: Gets token from memory, or hits the DB if null
    private suspend fun getGuardianToken(userId: String): String? {
        if (cachedToken != null) return cachedToken

        return try {
            val profile = SmartCareApp.supabase.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<Map<String, Any>>()

            cachedToken = profile?.get("device_token") as? String
            cachedToken
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch token from DB", e)
            null
        }
    }

    fun notifyGuardian(guardianUserId: String, title: String, body: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val token = getGuardianToken(guardianUserId)

            if (token == null) {
                Log.e(TAG, "❌ Critical: No token found. Alert will not reach Guardian.")
                return@launch
            }

            try {
                SmartCareApp.supabase.functions.invoke(
                    "send-alert",
                    body = mapOf("token" to token, "title" to title, "body" to body)
                )
                Log.d(TAG, "✅ Notification sent successfully")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Edge Function error", e)
            }
        }
    }

    fun notifySOS(guardianUserId: String, lat: Double, lng: Double) {
        val mapLink = "https://maps.google.com/?q=$lat,$lng"
        notifyGuardian(guardianUserId, "🚨 SOS EMERGENCY!", "Help needed! Location: $mapLink")
    }
}