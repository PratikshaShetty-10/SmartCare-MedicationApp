package com.yourteam.smartcare.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Medication(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String,
    @SerialName("dosage") val dosage: String,
    @SerialName("time") val time: String,
    @SerialName("status") val status: String, // e.g., "Pending", "Taken", "Missed"
    @SerialName("stock_left") val stockLeft: Int,
    @SerialName("total_stock") val totalStock: Int
)