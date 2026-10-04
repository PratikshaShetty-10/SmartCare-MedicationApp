package com.yourteam.smartcare.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourteam.smartcare.SmartCareApp
import com.yourteam.smartcare.api.EmergencyAlert
import com.yourteam.smartcare.api.MedicationScheduleNotifier
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.data.model.Relationship
import com.yourteam.smartcare.data.repository.MedicationRepository
import com.yourteam.smartcare.escalation.DoseAcknowledger
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@kotlinx.serialization.Serializable
data class GuardianNotification(
    @kotlinx.serialization.SerialName("id")
    val id: String? = null,

    @kotlinx.serialization.SerialName("guardian_id")
    val guardianId: String,

    @kotlinx.serialization.SerialName("elder_id")
    val elderId: String,

    @kotlinx.serialization.SerialName("title")
    val title: String,

    @kotlinx.serialization.SerialName("message")
    val message: String,

    @kotlinx.serialization.SerialName("type")
    val type: String,

    @kotlinx.serialization.SerialName("is_read")
    val isRead: Boolean = false,

    @kotlinx.serialization.SerialName("created_at")
    val createdAt: String? = null
)

sealed interface MedicationUiState {
    object Loading : MedicationUiState

    data class Success(
        val medications: List<Medication>
    ) : MedicationUiState

    data class Error(
        val message: String
    ) : MedicationUiState
}

class MedicationViewModel : ViewModel() {

    private val repository = MedicationRepository()

    private val medicationScheduleNotifier =
        MedicationScheduleNotifier()

    private val _uiState =
        MutableStateFlow<MedicationUiState>(MedicationUiState.Loading)

    val uiState: StateFlow<MedicationUiState> =
        _uiState.asStateFlow()

    // --- Emergency Tracking State ---
    private val _activeEmergency =
        MutableStateFlow<EmergencyAlert?>(null)

    val activeEmergency: StateFlow<EmergencyAlert?> =
        _activeEmergency.asStateFlow()

    // --- Notification Tracking State ---
    private val _notifications =
        MutableStateFlow<List<GuardianNotification>>(emptyList())

    val notifications: StateFlow<List<GuardianNotification>> =
        _notifications.asStateFlow()

    init {
        fetchTodaySchedule()
    }

    // --- Listen for SOS Alerts ---
    fun listenForEmergencies(guardianId: String) {
        viewModelScope.launch {
            try {
                while (true) {
                    val alert =
                        SupabaseClient.client
                            .from("emergency_alerts")
                            .select {
                                filter {
                                    eq("guardian_id", guardianId)
                                    eq("status", "ACTIVE")
                                }
                            }
                            .decodeList<EmergencyAlert>()
                            .firstOrNull()

                    _activeEmergency.value = alert

                    delay(5000)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Listen for Guardian Notifications ---
    fun listenForNotifications(guardianId: String) {
        viewModelScope.launch {
            try {
                while (true) {
                    val list =
                        SupabaseClient.client
                            .from("notifications")
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
                SupabaseClient.client
                    .from("notifications")
                    .update(
                        {
                            set("is_read", true)
                        }
                    ) {
                        filter {
                            eq("id", notificationId)
                        }
                    }

                _notifications.value =
                    _notifications.value.filter {
                        it.id != notificationId
                    }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissEmergency() {
        _activeEmergency.value = null
    }

    // --- Fetch medication schedule ---
    //
    // IMPORTANT:
    // Medication alarms are now handled by EscalationScheduler.
    // Do NOT use MedicationReminderManager here because that would
    // create a second/duplicate medication escalation system.
    fun fetchTodaySchedule(context: Context? = null) {
        viewModelScope.launch {
            _uiState.value = MedicationUiState.Loading

            try {
                val schedule = repository.getTodaySchedule()

                _uiState.value =
                    MedicationUiState.Success(schedule)

            } catch (e: Exception) {
                _uiState.value =
                    MedicationUiState.Error(
                        e.localizedMessage
                            ?: "Unknown error occurred"
                    )
            }
        }
    }

    // --- Send / Create Guardian Notification ---
    fun sendGuardianNotification(
        elderId: String,
        title: String,
        message: String,
        type: String
    ) {
        viewModelScope.launch {
            try {
                val relationship =
                    SupabaseClient.client
                        .from("relationships")
                        .select {
                            filter {
                                eq("elder_id", elderId)
                            }
                        }
                        .decodeSingleOrNull<Relationship>()

                val guardianId =
                    relationship?.guardianId
                        ?: return@launch

                val notification =
                    GuardianNotification(
                        guardianId = guardianId,
                        elderId = elderId,
                        title = title,
                        message = message,
                        type = type,
                        isRead = false
                    )

                SupabaseClient.client
                    .from("notifications")
                    .insert(notification)

                Log.d(
                    "MedVM",
                    "Notification sent successfully to guardian: $guardianId"
                )

            } catch (e: Exception) {
                Log.e(
                    "MedVM",
                    "Failed to send notification: ${e.localizedMessage}",
                    e
                )
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
                val currentUser =
                    SupabaseClient.client.auth.currentUserOrNull()

                if (currentUser == null) {
                    Log.e(
                        "MedVM",
                        "Add failed: No authenticated user found."
                    )

                    _uiState.value =
                        MedicationUiState.Error(
                            "User not logged in."
                        )

                    return@launch
                }

                val relationship =
                    SupabaseClient.client
                        .from("relationships")
                        .select {
                            filter {
                                eq("guardian_id", currentUser.id)
                            }
                        }
                        .decodeSingleOrNull<Relationship>()

                /*
                 * Guardian is the only user who adds medication.
                 * Never fall back to the Guardian's own ID.
                 */
                if (relationship == null) {
                    Log.e(
                        "MedVM",
                        "Add failed: No Elder relationship found for Guardian."
                    )

                    _uiState.value =
                        MedicationUiState.Error(
                            "No linked Elder found."
                        )

                    return@launch
                }

                val targetElderId =
                    relationship.elderId

                Log.d(
                    "MedVM",
                    "Adding medication for Elder ID: $targetElderId"
                )

                val newMedication =
                    Medication(
                        elderId = targetElderId,
                        medName = medName,
                        dosage = dosage,
                        scheduledTime = scheduledTime,
                        currentStock = currentStock,
                        isTaken = false
                    )

                SupabaseClient.client
                    .from("medications")
                    .insert(newMedication)

                Log.d(
                    "MedVM",
                    "Medication successfully inserted into Supabase."
                )

                /*
                 * Immediately notify the Elder device.
                 *
                 * The Elder will receive a data-only FCM and trigger
                 * ScheduleSync.enqueueNow().
                 *
                 * If FCM fails, the medication is still successfully
                 * saved. The existing 15-minute periodic sync remains
                 * the fallback.
                 */
                val notificationSent =
                    medicationScheduleNotifier.notifyElder(
                        targetElderId
                    )

                Log.d(
                    "MedVM",
                    "Medication schedule update notification sent=$notificationSent"
                )

                fetchTodaySchedule(context)

                onSuccess()

            } catch (e: Exception) {
                Log.e(
                    "MedVM",
                    "Exception during prescription insert: ${e.localizedMessage}",
                    e
                )

                _uiState.value =
                    MedicationUiState.Error(
                        "Failed to add medication: ${e.localizedMessage}"
                    )
            }
        }
    }

    // --- Mark Medication as Taken ---
    fun markAsTaken(
        medicationId: String,
        context: Context? = null
    ) {
        viewModelScope.launch {
            try {

                /*
                 * NEW MEDICATION ESCALATION SYSTEM
                 *
                 * DoseAcknowledger handles:
                 * - finding the current dose occurrence
                 * - marking it TAKEN locally
                 * - cancelling future escalation alarms
                 * - stopping TTS
                 * - stopping alarm audio
                 * - stopping vibration
                 * - cancelling the dose notification
                 * - syncing TAKEN through ack_dose
                 *
                 * We intentionally do NOT call:
                 *
                 * repository.recordAdherence(...)
                 *
                 * because ack_dose is responsible for writing the
                 * TAKEN adherence record.
                 */

                val appContext =
                    context ?: SmartCareApp.instance

                DoseAcknowledger.acknowledge(
                    context = appContext,
                    medicationId = medicationId
                )

                val currentState = _uiState.value

                if (currentState is MedicationUiState.Success) {

                    val medToUpdate =
                        currentState.medications.find {
                            it.id == medicationId
                        }

                    val newStock =
                        if (
                            medToUpdate != null &&
                            medToUpdate.currentStock > 0
                        ) {
                            medToUpdate.currentStock - 1
                        } else {
                            medToUpdate?.currentStock ?: 0
                        }

                    SupabaseClient.client
                        .from("medications")
                        .update(
                            {
                                set("is_taken", true)
                                set("current_stock", newStock)
                            }
                        ) {
                            filter {
                                eq("id", medicationId)
                            }
                        }
                }

                // Refresh medication schedule.
                //
                // This only refreshes the UI data.
                // It does NOT schedule the old MedicationReminderManager.
                fetchTodaySchedule(context)

            } catch (e: Exception) {

                Log.e(
                    "MedVM",
                    "Failed to mark medication as taken: ${e.localizedMessage}",
                    e
                )

                _uiState.value =
                    MedicationUiState.Error(
                        "Failed to update medication: ${e.message}"
                    )
            }
        }
    }

    // --- Delete Medication ---
    fun deleteMedication(
        medicationId: String,
        context: Context? = null
    ) {
        viewModelScope.launch {
            try {

                /*
                 * Get the medication first so we know which Elder
                 * must receive the schedule update.
                 */
                val medication =
                    SupabaseClient.client
                        .from("medications")
                        .select {
                            filter {
                                eq("id", medicationId)
                            }
                        }
                        .decodeSingleOrNull<Medication>()

                if (medication == null) {
                    Log.e(
                        "MedVM",
                        "Delete failed: Medication not found: $medicationId"
                    )

                    _uiState.value =
                        MedicationUiState.Error(
                            "Medication not found."
                        )

                    return@launch
                }

                val elderId =
                    medication.elderId

                SupabaseClient.client
                    .from("medications")
                    .delete {
                        filter {
                            eq("id", medicationId)
                        }
                    }

                Log.d(
                    "MedVM",
                    "Medication deleted successfully: $medicationId"
                )

                /*
                 * Immediately tell the Elder to refresh the schedule.
                 *
                 * If FCM fails, the existing periodic ScheduleSync
                 * remains the fallback.
                 */
                val notificationSent =
                    medicationScheduleNotifier.notifyElder(
                        elderId
                    )

                Log.d(
                    "MedVM",
                    "Medication delete schedule update sent=$notificationSent"
                )

                fetchTodaySchedule(context)

            } catch (e: Exception) {

                Log.e(
                    "MedVM",
                    "Failed to delete medication: ${e.localizedMessage}",
                    e
                )

                _uiState.value =
                    MedicationUiState.Error(
                        "Failed to delete medication: ${e.message}"
                    )
            }
        }
    }

    // --- Update Medication ---
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

                /*
                 * Get the medication first so we know which Elder
                 * must receive the schedule update.
                 */
                val medication =
                    SupabaseClient.client
                        .from("medications")
                        .select {
                            filter {
                                eq("id", medicationId)
                            }
                        }
                        .decodeSingleOrNull<Medication>()

                if (medication == null) {
                    Log.e(
                        "MedVM",
                        "Update failed: Medication not found: $medicationId"
                    )

                    _uiState.value =
                        MedicationUiState.Error(
                            "Medication not found."
                        )

                    return@launch
                }

                val elderId =
                    medication.elderId

                SupabaseClient.client
                    .from("medications")
                    .update(
                        {
                            set(
                                "med_name",
                                newName
                            )

                            set(
                                "dosage",
                                newDosage
                            )

                            set(
                                "scheduled_time",
                                newTime
                            )

                            set(
                                "current_stock",
                                newStock
                            )
                        }
                    ) {
                        filter {
                            eq(
                                "id",
                                medicationId
                            )
                        }
                    }

                Log.d(
                    "MedVM",
                    "Medication updated successfully: $medicationId"
                )

                /*
                 * Immediately tell the Elder to refresh the changed
                 * medication schedule.
                 *
                 * If FCM fails, the existing periodic ScheduleSync
                 * remains the fallback.
                 */
                val notificationSent =
                    medicationScheduleNotifier.notifyElder(
                        elderId
                    )

                Log.d(
                    "MedVM",
                    "Medication update schedule notification sent=$notificationSent"
                )

                fetchTodaySchedule(context)

            } catch (e: Exception) {

                Log.e(
                    "MedVM",
                    "Failed to update medication: ${e.localizedMessage}",
                    e
                )

                _uiState.value =
                    MedicationUiState.Error(
                        "Failed to update medication: ${e.message}"
                    )
            }
        }
    }
}