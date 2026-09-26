package com.yourteam.smartcare.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.smartcare.api.EmergencyAlert
import com.yourteam.smartcare.api.MedicationReminderManager
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.data.model.Relationship
import com.yourteam.smartcare.data.repository.MedicationRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@kotlinx.serialization.Serializable
data class GuardianNotification(
    @kotlinx.serialization.SerialName("id") val id: String? = null,
    @kotlinx.serialization.SerialName("guardian_id") val guardianId: String,
    @kotlinx.serialization.SerialName("elder_id") val elderId: String,
    @kotlinx.serialization.SerialName("title") val title: String,
    @kotlinx.serialization.SerialName("message") val message: String,
    @kotlinx.serialization.SerialName("type") val type: String,
    @kotlinx.serialization.SerialName("is_read") val isRead: Boolean = false,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null
)

sealed interface MedicationUiState {
    object Loading : MedicationUiState
    data class Success(val medications: List<Medication>) : MedicationUiState
    data class Error(val message: String) : MedicationUiState
}

class MedicationViewModel : ViewModel() {
    private val repository = MedicationRepository()

    private val _uiState = MutableStateFlow<MedicationUiState>(MedicationUiState.Loading)
    val uiState: StateFlow<MedicationUiState> = _uiState.asStateFlow()

    // --- Emergency Tracking State ---
    private val _activeEmergency = MutableStateFlow<EmergencyAlert?>(null)
    val activeEmergency: StateFlow<EmergencyAlert?> = _activeEmergency.asStateFlow()

    // --- Notification Tracking State ---
    private val _notifications = MutableStateFlow<List<GuardianNotification>>(emptyList())
    val notifications: StateFlow<List<GuardianNotification>> = _notifications.asStateFlow()

    init {
        fetchTodaySchedule()
    }

    // --- Listen for SOS Alerts ---
    fun listenForEmergencies(guardianId: String) {
        viewModelScope.launch {
            try {
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
                    delay(5000)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Listen for Guardian Notifications (Overdose/Mismatches) ---
    fun listenForNotifications(guardianId: String) {
        viewModelScope.launch {
            try {
                while (true) {
                    val list = SupabaseClient.client.from("notifications")
                        .select {
                            filter {
                                eq("guardian_id", guardianId)
                                eq("is_read", false)
                            }
                        }
                        .decodeList<GuardianNotification>()

                    _notifications.value = list
                    delay(5000)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Dismiss / Mark Notification as Read ---
    fun dismissNotification(notificationId: String) {
        viewModelScope.launch {
            try {
                SupabaseClient.client.from("notifications").update(
                    {
                        set("is_read", true)
                    }
                ) {
                    filter { eq("id", notificationId) }
                }
                _notifications.value = _notifications.value.filter { it.id != notificationId }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissEmergency() {
        _activeEmergency.value = null
    }

    // Fetch schedule & register Android Alarms
    fun fetchTodaySchedule(context: Context? = null) {
        viewModelScope.launch {
            _uiState.value = MedicationUiState.Loading
            try {
                val schedule = repository.getTodaySchedule()
                _uiState.value = MedicationUiState.Success(schedule)

                if (context != null && schedule.isNotEmpty()) {
                    val reminderManager = MedicationReminderManager(context)
                    reminderManager.scheduleAlarmsForToday(schedule)
                }

            } catch (e: Exception) {
                _uiState.value = MedicationUiState.Error(e.localizedMessage ?: "Unknown error occurred")
            }
        }
    }

    // --- Send / Create Guardian Notification (Overdose / Mismatch) ---
    fun sendGuardianNotification(
        elderId: String,
        title: String,
        message: String,
        type: String
    ) {
        viewModelScope.launch {
            try {
                val relationship = SupabaseClient.client.from("relationships")
                    .select {
                        filter { eq("elder_id", elderId) }
                    }
                    .decodeSingleOrNull<Relationship>()

                val guardianId = relationship?.guardianId ?: return@launch

                val notification = GuardianNotification(
                    guardianId = guardianId,
                    elderId = elderId,
                    title = title,
                    message = message,
                    type = type,
                    isRead = false
                )

                SupabaseClient.client.from("notifications").insert(notification)
                Log.d("MedVM", "Notification sent successfully to guardian: $guardianId")
            } catch (e: Exception) {
                Log.e("MedVM", "Failed to send notification: ${e.localizedMessage}", e)
            }
        }
    }

    // --- Add New Prescription ---
    fun addMedication(
        medName: String,
        dosage: String,
        scheduledTime: String,
        currentStock: Int,
        context: Context? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val currentUser = SupabaseClient.client.auth.currentUserOrNull()
                if (currentUser == null) {
                    Log.e("MedVM", "Add failed: No authenticated user found.")
                    _uiState.value = MedicationUiState.Error("User not logged in.")
                    return@launch
                }

                val relationship = SupabaseClient.client.from("relationships")
                    .select {
                        filter { eq("guardian_id", currentUser.id) }
                    }
                    .decodeSingleOrNull<Relationship>()

                val targetElderId = relationship?.elderId ?: currentUser.id
                Log.d("MedVM", "Adding medication for target ID: $targetElderId")

                val newMedication = Medication(
                    elderId = targetElderId,
                    medName = medName,
                    dosage = dosage,
                    scheduledTime = scheduledTime,
                    currentStock = currentStock,
                    isTaken = false
                )

                SupabaseClient.client.from("medications").insert(newMedication)
                Log.d("MedVM", "Medication successfully inserted into Supabase.")

                fetchTodaySchedule(context)
                onSuccess()
            } catch (e: Exception) {
                Log.e("MedVM", "Exception during prescription insert: ${e.localizedMessage}", e)
                _uiState.value = MedicationUiState.Error("Failed to add medication: ${e.localizedMessage}")
            }
        }
    }

    fun markAsTaken(medicationId: String, context: Context? = null) {
        viewModelScope.launch {
            try {
                repository.recordAdherence(medicationId, "taken")

                val currentState = _uiState.value
                if (currentState is MedicationUiState.Success) {
                    val medToUpdate = currentState.medications.find { it.id == medicationId }

                    val newStock = if (medToUpdate != null && medToUpdate.currentStock > 0) {
                        medToUpdate.currentStock - 1
                    } else {
                        medToUpdate?.currentStock ?: 0
                    }

                    SupabaseClient.client.from("medications").update(
                        {
                            set("is_taken", true)
                            set("current_stock", newStock)
                        }
                    ) { filter { eq("id", medicationId) } }
                }

                fetchTodaySchedule(context)
            } catch (e: Exception) {
                _uiState.value = MedicationUiState.Error("Failed to update medication: ${e.message}")
            }
        }
    }

    fun deleteMedication(medicationId: String, context: Context? = null) {
        viewModelScope.launch {
            try {
                SupabaseClient.client.from("medications").delete {
                    filter { eq("id", medicationId) }
                }
                fetchTodaySchedule(context)
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
        newStock: Int,
        context: Context? = null
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
                fetchTodaySchedule(context)
            } catch (e: Exception) {
                _uiState.value = MedicationUiState.Error("Failed to update medication: ${e.message}")
            }
        }
    }
}