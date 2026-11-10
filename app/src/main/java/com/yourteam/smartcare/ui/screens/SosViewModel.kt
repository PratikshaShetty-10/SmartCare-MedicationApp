
package com.yourteam.smartcare.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.smartcare.data.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Data class for the Relationships table lookup
@Serializable
data class RelationshipRecord(
    val guardian_id: String? = null
)

class SosViewModel : ViewModel() {

    private val _guardianId = MutableStateFlow<String?>(null)
    val guardianId: StateFlow<String?> = _guardianId

    private val _statusText =
        MutableStateFlow("Press SOS to alert Guardian")
    val statusText: StateFlow<String> = _statusText

    // Fetch the linked Guardian when the SOS screen opens
    fun fetchEmergencyData() {
        viewModelScope.launch {
            try {
                _statusText.value = "Finding Guardian..."

                val elderId =
                    SupabaseClient.client.auth.currentUserOrNull()?.id

                if (elderId == null) {
                    _statusText.value =
                        "Error: App thinks you are logged out"
                    return@launch
                }

                // Find the Guardian linked to the Elder
                val relationship = SupabaseClient.client
                    .from("relationships")
                    .select(Columns.list("guardian_id")) {
                        filter {
                            eq("elder_id", elderId)
                        }
                    }
                    .decodeList<RelationshipRecord>()
                    .firstOrNull()

                val targetGuardianId = relationship?.guardian_id

                if (targetGuardianId.isNullOrBlank()) {
                    _guardianId.value = null
                    _statusText.value = "Error: No Guardian linked."
                    return@launch
                }

                _guardianId.value = targetGuardianId
                _statusText.value = "Press SOS to alert Guardian"

            } catch (e: Exception) {
                e.printStackTrace()
                _statusText.value =
                    "Error: Unable to fetch Guardian details."
            }
        }
    }
}