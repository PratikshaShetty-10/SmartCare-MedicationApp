package com.yourteam.smartcare.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.smartcare.SmartCareApp
import com.yourteam.smartcare.api.EmergencyPayload
import com.yourteam.smartcare.api.NotificationClient
import com.yourteam.smartcare.emergency.services.EscalationManager
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SOSViewModel(
    application: Application,
    private val elderId: String,
    private val guardianUserId: String,
    private val locationViewModel: LocationViewModel,
    private val notificationClient: NotificationClient,
    private val escalationManager: EscalationManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<SOSUiState>(SOSUiState.Idle)
    val uiState: StateFlow<SOSUiState> = _uiState

    fun triggerSOS() {
        if (_uiState.value is SOSUiState.Loading) return

        viewModelScope.launch {
            _uiState.value = SOSUiState.Loading
            val location = locationViewModel.currentLocation.value

            if (location == null) {
                _uiState.value = SOSUiState.Error("GPS signal not found.")
                return@launch
            }

            escalationManager.stopAll()

            val payload = EmergencyPayload(elderId, location.latitude, location.longitude, System.currentTimeMillis())
            val wasNotified = notificationClient.sendSosAlert(payload)

            logEmergencyToSupabase(elderId, location.latitude, location.longitude)

            _uiState.value = if (wasNotified) {
                SOSUiState.Success("SOS Alert Sent!")
            } else {
                SOSUiState.Error("Notification failed.")
            }
        }
    }

    private suspend fun logEmergencyToSupabase(id: String, lat: Double, lng: Double) {
        SmartCareApp.supabase.from("emergency_logs").insert(mapOf(
            "elder_id" to id, "location_lat" to lat, "location_lng" to lng
        ))
    }
    sealed class SOSUiState {
        object Idle : SOSUiState()
        object Loading : SOSUiState()
        data class Success(val message: String) : SOSUiState()
        data class Error(val message: String) : SOSUiState()
    }
}