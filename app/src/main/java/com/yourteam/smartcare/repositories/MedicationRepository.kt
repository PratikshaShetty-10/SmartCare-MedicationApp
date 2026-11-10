package com.yourteam.smartcare.repositories

// 1. Imported our custom supabase client instead of SmartCareApp
import com.yourteam.smartcare.utils.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable


@Serializable
data class MedicationModel(val id: Int, val name: String, val stockCount: Int)

class MedicationRepository {
    suspend fun getRemainingStock(): Int {
        return try {
            // 2. Removed 'SmartCareApp.' prefix
            val response = supabase.from("medications")
                .select { single() }
                .decodeSingle<MedicationModel>()
            response.stockCount
        } catch (e: Exception) {
            0
        }
    }
}