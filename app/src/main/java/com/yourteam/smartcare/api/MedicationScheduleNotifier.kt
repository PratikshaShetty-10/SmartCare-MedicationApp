package com.yourteam.smartcare.api

import android.util.Log
import com.yourteam.smartcare.data.SupabaseClient
import io.github.jan.supabase.functions.functions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MedicationScheduleUpdatePayload(
    @SerialName("elder_id")
    val elderId: String
)

class MedicationScheduleNotifier {

    companion object {
        private const val TAG = "MedicationScheduleNotifier"
    }

    suspend fun notifyElder(elderId: String): Boolean {
        return try {

            val response = SupabaseClient.client.functions.invoke(
                "send-medication-schedule-update",
                body = MedicationScheduleUpdatePayload(elderId)
            )

            Log.d(
                TAG,
                "Schedule update request completed. Elder ID=$elderId"
            )

            Log.d(
                TAG,
                "Edge Function response=$response"
            )

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to send medication schedule update: ${e.message}",
                e
            )

            false
        }
    }
}