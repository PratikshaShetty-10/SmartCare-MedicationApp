package com.yourteam.smartcare.data

// Change "jan_tennert" to "jan"
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MedicationRepository {

    suspend fun getTodaySchedule(): List<Medication> = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("medications")
            .select()
            .decodeList<Medication>()
    }

    suspend fun updateMedicationStatus(id: Int, newStatus: String) = withContext(Dispatchers.IO) {
        SupabaseProvider.client.from("medications")
            .update({
                set("status", newStatus)
            }) {
                filter {
                    eq("id", id)
                }
            }
    }
}