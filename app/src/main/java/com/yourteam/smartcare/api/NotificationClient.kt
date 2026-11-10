package com.yourteam.smartcare.api

import io.github.jan.supabase.functions.Functions
import kotlinx.serialization.Serializable

@Serializable
data class EmergencyPayload(
    val elderId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

class NotificationClient(private val functions: Functions) {
    suspend fun sendSosAlert(payload: EmergencyPayload): Boolean {
        return try {
            functions.invoke("trigger-sos-escalation", body = payload)
            true
        } catch (e: Exception) {
            false
        }
    }
}