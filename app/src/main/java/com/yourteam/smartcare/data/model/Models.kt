package com.yourteam.smartcare.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("role") val role: String, // "elder" or "guardian"
    @SerialName("phone") val phone: String,
    @SerialName("pair_code") val pairCode: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Relationship(
    @SerialName("id") val id: String? = null,
    @SerialName("guardian_id") val guardianId: String,
    @SerialName("elder_id") val elderId: String,
    @SerialName("medical_history") val medicalHistory: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Medication(
    @SerialName("id") val id: String? = null,
    @SerialName("elder_id") val elderId: String,
    @SerialName("med_name") val medName: String,
    @SerialName("dosage") val dosage: String,
    @SerialName("scheduled_time") val scheduledTime: String,
    @SerialName("min_interval_hours") val minIntervalHours: Int = 4,
    @SerialName("current_stock") val currentStock: Int = 0,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AdherenceLog(
    @SerialName("id") val id: String? = null,
    @SerialName("medication_id") val medicationId: String,
    @SerialName("scheduled_at") val scheduledAt: String,
    @SerialName("taken_at") val takenAt: String? = null,
    @SerialName("status") val status: String = "pending"
)

@Serializable
data class VerificationLog(
    @SerialName("id") val id: String? = null,
    @SerialName("medication_id") val medicationId: String,
    @SerialName("adherence_log_id") val adherenceLogId: String? = null,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("expected_count") val expectedCount: Int,
    @SerialName("actual_count_yolo") val actualCountYolo: Int,
    @SerialName("ocr_expiry_date") val ocrExpiryDate: String? = null,
    @SerialName("mismatch_flag") val mismatchFlag: Boolean = false
)