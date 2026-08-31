package com.yourteam.smartcare.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// We removed the "utils.supabase" import and replaced it with your real data client!
import com.yourteam.smartcare.data.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class RelationshipRecord(val guardian_id: String? = null)

class SosViewModel : ViewModel() {
    private val _guardianId = MutableStateFlow<String?>(null)
    val guardianId: StateFlow<String?> = _guardianId

    private val _statusText = MutableStateFlow("Loading Guardian data...")
    val statusText: StateFlow<String> = _statusText

    fun fetchEmergencyData() {
        viewModelScope.launch {
            try {
                _statusText.value = "Checking login status..."

                // 1. Check the REAL SupabaseClient that the rest of the app uses
                val elderId = SupabaseClient.client.auth.currentUserOrNull()?.id

                if (elderId == null) {
                    _statusText.value = "Error: App thinks you are logged out"
                    return@launch
                }

                // 2. Search the RELATIONSHIP table using the REAL SupabaseClient
                val relationship = SupabaseClient.client.from("relationships")
                    .select(Columns.list("guardian_id")) {
                        filter { eq("elder_id", elderId) }
                    }
                    .decodeList<RelationshipRecord>().firstOrNull()

                // 3. Analyze the result
                if (relationship == null) {
                    _statusText.value = "Error: No matching elder_id in relationships table."
                } else if (relationship.guardian_id.isNullOrBlank()) {
                    _statusText.value = "Error: Relationship found, but guardian_id column is blank."
                } else {
                    _guardianId.value = relationship.guardian_id
                    _statusText.value = "Guardian Ready"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _statusText.value = "Crash: ${e.localizedMessage ?: "Unknown Error"}"
            }
        }
    }
}