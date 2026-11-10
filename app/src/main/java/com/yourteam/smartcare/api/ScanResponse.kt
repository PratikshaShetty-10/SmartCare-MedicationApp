package com.yourteam.smartcare.api

import com.google.gson.annotations.SerializedName

data class ScanResponse(
    @SerializedName("actual_count_yolo") val actualCountYolo: Int,
    @SerializedName("ok_pills") val okPills: Int,
    @SerializedName("broken_pills") val brokenPills: Int,
    @SerializedName("empty_slots") val emptySlots: Int,
    @SerializedName("total_objects") val totalObjects: Int,

    @SerializedName("ocr_text") val ocrText: String?,
    @SerializedName("manufacturing_date") val manufacturingDate: String?,
    @SerializedName("ocr_expiry_date") val ocrExpiryDate: String?,
    @SerializedName("expiry_status") val expiryStatus: String,

    @SerializedName("condition_detections") val conditionDetections: List<ConditionDetection>,
    @SerializedName("ocr_detections") val ocrDetections: List<OcrDetection>
)

data class ConditionDetection(
    @SerializedName("class") val className: String,
    @SerializedName("confidence") val confidence: Double
)

data class OcrDetection(
    @SerializedName("character") val character: String,
    @SerializedName("confidence") val confidence: Double
)