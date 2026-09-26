package com.yourteam.smartcare.ui.screens

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.auth.oauth2.GoogleCredentials
import com.yourteam.smartcare.R // 🚨 IMPORTANT: Make sure this matches your actual package name!
import com.yourteam.smartcare.data.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// 1. Data class for the Relationships table lookup
@Serializable
data class RelationshipRecord(val guardian_id: String? = null)

// 2. Data class for the Profiles table lookup (where the FCM token lives)
@Serializable
data class ProfileRecord(val fcm_token: String? = null)

class SosViewModel : ViewModel() {
    private val _guardianId = MutableStateFlow<String?>(null)
    val guardianId: StateFlow<String?> = _guardianId

    private val _statusText = MutableStateFlow("Press SOS to alert Guardian")
    val statusText: StateFlow<String> = _statusText

    // Call this from your UI when the SOS button is clicked
    fun triggerSosAlert(context: Context) {
        viewModelScope.launch {
            try {
                _statusText.value = "Finding Guardian..."

                val elderId = SupabaseClient.client.auth.currentUserOrNull()?.id
                if (elderId == null) {
                    _statusText.value = "Error: App thinks you are logged out"
                    return@launch
                }

                // STEP 1: Find WHO the guardian is (from relationships table)
                val relationship = SupabaseClient.client.from("relationships")
                    .select(Columns.list("guardian_id")) {
                        filter { eq("elder_id", elderId) }
                    }
                    .decodeList<RelationshipRecord>().firstOrNull()

                val targetGuardianId = relationship?.guardian_id
                if (targetGuardianId == null) {
                    _statusText.value = "Error: No Guardian linked."
                    return@launch
                }

                _guardianId.value = targetGuardianId

                // STEP 2: Find WHERE their phone is (from profiles table)
                val profile = SupabaseClient.client.from("profiles")
                    .select(Columns.list("fcm_token")) {
                        filter { eq("id", targetGuardianId) }
                    }
                    .decodeList<ProfileRecord>().firstOrNull()

                val token = profile?.fcm_token
                if (token.isNullOrBlank()) {
                    _statusText.value = "Error: Guardian hasn't registered their phone for alerts."
                    return@launch
                }

                // STEP 3: Fire the alert over the network to Firebase using modern HTTP v1
                _statusText.value = "Dispatching Alarm..."
                val success = sendPushNotificationToFirebase(context, token)

                if (success) {
                    _statusText.value = "✅ Guardian Alerted Successfully!"
                } else {
                    _statusText.value = "❌ Failed to send alert over network."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _statusText.value = "Crash: ${e.localizedMessage ?: "Unknown Error"}"
            }
        }
    }

    // The background network call to Firebase Cloud Messaging (Modern HTTP v1)
    private suspend fun sendPushNotificationToFirebase(context: Context, guardianToken: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Read the JSON file and generate a secure temporary password
                val stream = context.resources.openRawResource(R.raw.firebase_key)
                val credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))

                credentials.refreshIfExpired()
                val accessToken = credentials.accessToken.tokenValue

                // 2. Format the payload for the NEW Firebase API
                val json = JSONObject().apply {
                    put("message", JSONObject().apply {
                        put("token", guardianToken)
                        put("notification", JSONObject().apply {
                            put("title", "🚨 EMERGENCY ALERT 🚨")
                            put("body", "The elder has triggered the SOS button and needs immediate assistance!")
                        })
                    })
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                // Note: The project ID "smartcare-ffe6c" is already hardcoded in the URL for you
                val request = Request.Builder()
                    .url("https://fcm.googleapis.com/v1/projects/smartcare-ffe6c/messages:send")
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = OkHttpClient().newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("SOS_NETWORK", "FCM Error: ${response.body?.string()}")
                }

                response.isSuccessful
            } catch (e: Exception) {
                Log.e("SOS_NETWORK", "Failed to ping Firebase v1 API", e)
                false
            }
        }
    }
    fun fetchEmergencyData() {
        viewModelScope.launch {
            try {
                val elderId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@launch
                val relationship = SupabaseClient.client.from("relationships")
                    .select(Columns.list("guardian_id")) {
                        filter { eq("elder_id", elderId) }
                    }
                    .decodeList<RelationshipRecord>().firstOrNull()

                _guardianId.value = relationship?.guardian_id
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}