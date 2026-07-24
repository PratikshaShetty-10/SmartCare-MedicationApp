package com.yourteam.smartcare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.smartcare.data.Medication
import com.yourteam.smartcare.data.MedicationRepository
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

    fun markAsTaken(id: Int) {
        viewModelScope.launch {
            try {
                repository.updateMedicationStatus(id, "Taken")
                fetchTodaySchedule()
            } catch (e: Exception) {
                _uiState.value = MedicationUiState.Error("Failed to update medication status")
            }
        }
    }
}