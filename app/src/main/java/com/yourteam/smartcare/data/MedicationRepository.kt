package com.yourteam.smartcare.data.repository

import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.AdherenceLog
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.data.model.Relationship
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class MedicationRepository {

    suspend fun getTodaySchedule(): List<Medication> = withContext(Dispatchers.IO) {
        val currentUser = SupabaseClient.client.auth.currentUserOrNull()
            ?: return@withContext emptyList()

        // Check if the current user is a guardian linked to an elder
        val relationship = SupabaseClient.client.from("relationships")
            .select {
                filter { eq("guardian_id", currentUser.id) }
            }
            .decodeSingleOrNull<Relationship>()

        val targetElderId = relationship?.elderId ?: currentUser.id

        // Fetch medications for the resolved target ID
        SupabaseClient.client.from("medications")
            .select {
                filter { eq("elder_id", targetElderId) }
            }
            .decodeList<Medication>()
    }

    suspend fun recordAdherence(medicationId: String, status: String = "taken") = withContext(Dispatchers.IO) {
        val log = AdherenceLog(
            medicationId = medicationId,
            scheduledAt = Instant.now().toString(),
            takenAt = Instant.now().toString(),
            status = status
        )
        SupabaseClient.client.from("adherence_logs").insert(log)
    }

    suspend fun addMedication(medication: Medication) = withContext(Dispatchers.IO) {
        SupabaseClient.client.from("medications").insert(medication)
    }
}