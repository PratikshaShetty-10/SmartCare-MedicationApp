package com.yourteam.smartcare.repositories

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.yourteam.smartcare.api.ApiConfig
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

@Serializable
data class VerificationRequest(val image: String, val medication_id: String)

@Serializable
data class VerificationResponse(val verified: Boolean, val pillCount: Int = 0, val expiryDate: String? = null, val message: String = "")

class VerificationRepository(private val client: HttpClient, private val json: Json) {

    suspend fun verifyMedication(imageBitmap: Bitmap, medicationId: String): VerificationResponse {
        if (ApiConfig.USE_MOCK_AI) return mockVerify(medicationId)

        val request = VerificationRequest(bitmapToCompressedBase64(imageBitmap), medicationId)

        repeat(ApiConfig.MAX_RETRIES + 1) {
            try {
                val response = client.post(ApiConfig.AI_VERIFY_URL) {
                    setBody(json.encodeToString(VerificationRequest.serializer(), request))
                }
                return json.decodeFromString(VerificationResponse.serializer(), response.bodyAsText())
            } catch (e: Exception) {
                Log.e("VerificationRepo", "Attempt failed: ${e.message}")
            }
        }
        return VerificationResponse(verified = false, message = "Server unreachable.")
    }

    private fun bitmapToCompressedBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun mockVerify(medicationId: String): VerificationResponse {
        delay(500)
        return VerificationResponse(verified = true, message = "Mock success")
    }
}