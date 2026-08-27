package com.yourteam.smartcare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.data.repository.MedicationRepository
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MedicationUiState {
    object Loading : MedicationUiState
    data class Success(val medications: List<Medication>) : MedicationUiState
    data class Error(val message: String) : MedicationUiState
}

class MedicationViewModel : ViewModel() {
    private val repository = MedicationRepository()

    private val _uiState = MutableStateFlow<MedicationUiState>(MedicationUiState.Loading)
    val uiState: StateFlow<MedicationUiState> = _uiState.asStateFlow()

    init {
        fetchTodaySchedule()
    }

    fun fetchTodaySchedule() {
        viewModelScope.launch {
            _uiState.value = MedicationUiState.Loading
            try {
                val schedule = repository.getTodaySchedule()
                _uiState.value = MedicationUiState.Success(schedule)
            } catch (e: Exception) {
                _uiState.value = MedicationUiState.Error(e.localizedMessage ?: "Unknown error occurred")
            }
        }
    }

    fun markAsTaken(medicationId: String) {
        viewModelScope.launch {
            try {
                // 1. Log the adherence history
                repository.recordAdherence(medicationId, "taken")

                // 2. Find the current stock and subtract 1
                val currentState = _uiState.value
                if (currentState is MedicationUiState.Success) {
                    val medToUpdate = currentState.medications.find { it.id == medicationId }

                    if (medToUpdate != null && medToUpdate.currentStock > 0) {
                        val newStock = medToUpdate.currentStock - 1

                        // 3. Update the stock count in Supabase
                        // Note: If your database column uses an underscore, change "currentStock" to "current_stock"
                        SupabaseClient.client.from("medications").update(
                            {
                                set("current_stock", newStock)
                            }
                        ) {
                            filter { eq("id", medicationId) }
                        }
                    }
                }

                // 4. Refresh the UI with the new data
                fetchTodaySchedule()
            } catch (e: Exception) {
                _uiState.value = MedicationUiState.Error("Failed to update medication: ${e.message}")
            }
        }
    }
    // --- NEW: Delete Medication ---
    fun deleteMedication(medicationId: String) {
        viewModelScope.launch {
            try {
                com.yourteam.smartcare.data.SupabaseClient.client.from("medications").delete {
                    filter { eq("id", medicationId) }
                }
                // Refresh the list after deleting
                fetchTodaySchedule()
            } catch (e: Exception) {
                _uiState.value = MedicationUiState.Error("Failed to delete medication: ${e.message}")
            }
        }
    }

    // --- NEW: Update Medication ---
    fun updateMedication(
        medicationId: String,
        newName: String,
        newDosage: String,
        newTime: String,
        newStock: Int
    ) {
        viewModelScope.launch {
            try {
                com.yourteam.smartcare.data.SupabaseClient.client.from("medications").update(
                    {
                        // Note: If you get an error about column names later,
                        // you may need to change these to "med_name" or "scheduled_time"
                        set("med_name", newName)
                        set("dosage", newDosage)
                        set("scheduled_time", newTime)
                        set("current_stock", newStock)
                    }
                ) {
                    filter { eq("id", medicationId) }
                }
                // Refresh the list after updating
                fetchTodaySchedule()
            } catch (e: Exception) {
                _uiState.value = MedicationUiState.Error("Failed to update medication: ${e.message}")
            }
        }
    }
}