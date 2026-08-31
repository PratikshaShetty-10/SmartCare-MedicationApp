package com.yourteam.smartcare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.smartcare.api.EmergencyAlert
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.data.repository.MedicationRepository
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.delay
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

    // --- NEW: Emergency Tracking State ---
    private val _activeEmergency = MutableStateFlow<EmergencyAlert?>(null)
    val activeEmergency: StateFlow<EmergencyAlert?> = _activeEmergency.asStateFlow()

    init {
        fetchTodaySchedule()
    }

    // --- NEW: Listen for SOS Alerts ---
    fun listenForEmergencies(guardianId: String) {
        viewModelScope.launch {
            try {
                // Polls the database every 5 seconds to see if the Elder hit the button
                while (true) {
                    val alert = SupabaseClient.client.from("emergency_alerts")
                        .select {
                            filter {
                                eq("guardian_id", guardianId)
                                eq("status", "ACTIVE")
                            }
                        }
                        .decodeList<EmergencyAlert>().firstOrNull()

                    _activeEmergency.value = alert
                    delay(5000) // Check every 5 seconds
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissEmergency() {
        // Clear the alert from the UI
        _activeEmergency.value = null
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
                repository.recordAdherence(medicationId, "taken")
                val currentState = _uiState.value
                if (currentState is MedicationUiState.Success) {
                    val medToUpdate = currentState.medications.find { it.id == medicationId }

                    if (medToUpdate != null && medToUpdate.currentStock > 0) {
                        val newStock = medToUpdate.currentStock - 1
                        SupabaseClient.client.from("medications").update(
                            { set("current_stock", newStock) }
                        ) { filter { eq("id", medicationId) } }
                    }
                }
                fetchTodaySchedule()
            } catch (e: Exception) {
                _uiState.value = MedicationUiState.Error("Failed to update medication: ${e.message}")
            }
        }
    }

    fun deleteMedication(medicationId: String) {
        viewModelScope.launch {
            try {
                SupabaseClient.client.from("medications").delete {
                    filter { eq("id", medicationId) }
                }
                fetchTodaySchedule()
            } catch (e: Exception) {
                _uiState.value = MedicationUiState.Error("Failed to delete medication: ${e.message}")
            }
        }
    }

    fun updateMedication(
        medicationId: String,
        newName: String,
        newDosage: String,
        newTime: String,
        newStock: Int
    ) {
        viewModelScope.launch {
            try {
                SupabaseClient.client.from("medications").update(
                    {
                        set("med_name", newName)
                        set("dosage", newDosage)
                        set("scheduled_time", newTime)
                        set("current_stock", newStock)
                    }
                ) {
                    filter { eq("id", medicationId) }
                }
                fetchTodaySchedule()
            } catch (e: Exception) {
                _uiState.value = MedicationUiState.Error("Failed to update medication: ${e.message}")
            }
        }
    }
}