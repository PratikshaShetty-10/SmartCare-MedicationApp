package com.yourteam.smartcare.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.smartcare.SmartCareApp
import com.yourteam.smartcare.utils.EscalationManager
import com.yourteam.smartcare.utils.GuardianNotifier
import com.yourteam.smartcare.utils.LocationHelper
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface SOSUiState {
    object Idle : SOSUiState
    object Loading : SOSUiState
    data class Success(val message: String) : SOSUiState
    data class Error(val message: String) : SOSUiState
}

class SOSViewModel(
    application: Application,
    private val elderId: String,
    private val guardianUserId: String
) : AndroidViewModel(application) {

    private val locationHelper = LocationHelper(getApplication())
    private val guardianNotifier = GuardianNotifier(getApplication())
    private val escalationManager = EscalationManager(getApplication())

    private val _uiState = MutableStateFlow<SOSUiState>(SOSUiState.Idle)
    val uiState: StateFlow<SOSUiState> = _uiState

    fun triggerSOS() {
        if (_uiState.value is SOSUiState.Loading) return

        viewModelScope.launch {
            _uiState.value = SOSUiState.Loading
            try {
                val location = locationHelper.getCurrentLocation()
                val lat = location?.latitude ?: 0.0
                val lng = location?.longitude ?: 0.0

                if (lat == 0.0 && lng == 0.0) {
                    _uiState.value = SOSUiState.Error("GPS unavailable")
                    return@launch
                }

                escalationManager.stopAll()

                // Notify Guardian
                guardianNotifier.notifySOS(guardianUserId, lat, lng)

                // Log to Database (including Address)
                logEmergencyToSupabase(elderId, lat, lng)

                _uiState.value = SOSUiState.Success("SOS Sent Successfully!")
            } catch (e: Exception) {
                Log.e("SOSViewModel", "SOS Process Failed", e)
                _uiState.value = SOSUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun logEmergencyToSupabase(elderId: String, lat: Double, lng: Double) {
        try {
            // This line ensures you get the address before saving
            val address = locationHelper.getAddressFromCoordinates(lat, lng)

            SmartCareApp.supabase.from("emergency_logs").insert(mapOf(
                "elder_id" to elderId,
                "location_lat" to lat,
                "location_lng" to lng,
                "address" to address, // Saved in the database
                "timestamp" to System.currentTimeMillis()
            ))
        } catch (e: Exception) {
            Log.e("SOSViewModel", "Supabase logging failed", e)
        }
    }
}