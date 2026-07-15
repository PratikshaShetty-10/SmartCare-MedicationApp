// MedicationRepository.kt
package com.yourteam.smartcare.repositories

import com.yourteam.smartcare.SmartCareApp
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

@Serializable
data class MedicationModel(val id: Int, val name: String, val stockCount: Int)

class MedicationRepository {
    suspend fun getRemainingStock(): Int {
        return try {
            // Query the 'medications' table
            val response = SmartCareApp.supabase.from("medications")
                .select { single() }
                .decodeSingle<MedicationModel>()
            response.stockCount
        } catch (e: Exception) {
            0
        }
    }
}