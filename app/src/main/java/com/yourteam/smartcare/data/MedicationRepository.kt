package com.yourteam.smartcare.data.repository

import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.AdherenceLog
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.data.model.Relationship
import com.yourteam.smartcare.data.model.VerificationLog
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class MedicationRepository {

    suspend fun getTodaySchedule(): List<Medication> =
        withContext(Dispatchers.IO) {
            val currentUser =
                SupabaseClient.client.auth.currentUserOrNull()
                    ?: return@withContext emptyList()

            // Check if the current user is a guardian linked to an elder.
            val relationship = SupabaseClient.client
                .from("relationships")
                .select {
                    filter {
                        eq("guardian_id", currentUser.id)
                    }
                }
                .decodeSingleOrNull<Relationship>()

            val targetElderId = relationship?.elderId ?: currentUser.id

            // Fetch medications for the resolved target ID.
            SupabaseClient.client
                .from("medications")
                .select {
                    filter {
                        eq("elder_id", targetElderId)
                    }
                }
                .decodeList<Medication>()
        }

    /*
     * P7 - Read-only adherence history.
     *
     * This only READS adherence_logs.
     * It does not create, update, or delete any adherence record.
     */
    suspend fun getAdherenceLogs(): List<AdherenceLog> =
        withContext(Dispatchers.IO) {
            val currentUser =
                SupabaseClient.client.auth.currentUserOrNull()
                    ?: return@withContext emptyList()

            val relationship = SupabaseClient.client
                .from("relationships")
                .select {
                    filter {
                        eq("guardian_id", currentUser.id)
                    }
                }
                .decodeSingleOrNull<Relationship>()

            relationship ?: return@withContext emptyList()

            val elderId = relationship.elderId

            val medications = SupabaseClient.client
                .from("medications")
                .select {
                    filter {
                        eq("elder_id", elderId)
                    }
                }
                .decodeList<Medication>()

            val medicationIds = medications.mapNotNull { it.id }

            if (medicationIds.isEmpty()) {
                return@withContext emptyList()
            }

            return@withContext SupabaseClient.client
                .from("adherence_logs")
                .select {
                    filter {
                        isIn("medication_id", medicationIds)
                    }
                }
                .decodeList<AdherenceLog>()
        }

    /**
     * Weekly scan-report support.
     *
     * Reads verification logs for medications belonging to
     * the elder linked to the currently signed-in guardian.
     * Does not modify scan or adherence records.
     */
    suspend fun getVerificationLogs(): List<VerificationLog> =
        withContext(Dispatchers.IO) {
            val currentUser =
                SupabaseClient.client.auth.currentUserOrNull()
                    ?: return@withContext emptyList()

            val relationship = SupabaseClient.client
                .from("relationships")
                .select {
                    filter {
                        eq("guardian_id", currentUser.id)
                    }
                }
                .decodeSingleOrNull<Relationship>()
                ?: return@withContext emptyList()

            val medications = SupabaseClient.client
                .from("medications")
                .select {
                    filter {
                        eq("elder_id", relationship.elderId)
                    }
                }
                .decodeList<Medication>()

            val medicationIds = medications.mapNotNull { it.id }

            if (medicationIds.isEmpty()) {
                return@withContext emptyList()
            }

            SupabaseClient.client
                .from("verification_logs")
                .select {
                    filter {
                        isIn("medication_id", medicationIds)
                    }
                }
                .decodeList<VerificationLog>()
        }

    suspend fun recordAdherence(
        medicationId: String,
        status: String = "taken"
    ) = withContext(Dispatchers.IO) {
        val log = AdherenceLog(
            medicationId = medicationId,
            scheduledAt = Instant.now().toString(),
            takenAt = Instant.now().toString(),
            status = status
        )

        SupabaseClient.client
            .from("adherence_logs")
            .insert(log)
    }

    suspend fun addMedication(medication: Medication) =
        withContext(Dispatchers.IO) {
            SupabaseClient.client
                .from("medications")
                .insert(medication)
        }

    /**
     * Expiry scanner support.
     *
     * Updates only expiry_date for the selected medication.
     * Does not intentionally modify stock, dosage, schedules,
     * adherence records, or verification logs.
     */
    suspend fun updateMedicationExpiryDate(
        medicationId: String,
        confirmedExpiryDate: String
    ) = withContext(Dispatchers.IO) {
        require(medicationId.isNotBlank()) {
            "Medication ID cannot be empty"
        }

        require(confirmedExpiryDate.isNotBlank()) {
            "Confirmed expiry date cannot be empty"
        }

        SupabaseClient.client
            .from("medications")
            .update(
                mapOf("expiry_date" to confirmedExpiryDate)
            ) {
                filter {
                    eq("id", medicationId)
                }
            }
    }
}