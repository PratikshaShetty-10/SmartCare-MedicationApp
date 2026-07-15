package com.yourteam.smartcare.repositories

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.yourteam.smartcare.utils.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream


@Serializable
data class VerificationRequest(
    val image: String, // Base64 encoded, compressed JPEG
    val medication_id: String
)

@Serializable
data class VerificationResponse(
    val verified: Boolean,
    val pillCount: Int = 0,
    val expiryDate: String? = null,
    val message: String = ""
)

class VerificationRepository {

    companion object {
        private const val TAG = "VerificationRepository"
        private const val MAX_IMAGE_DIMENSION = 1024 // px, keeps upload small & fast
        private const val JPEG_QUALITY = 80
    }

    // 🔥 LAZY INIT - The client is only created when we ACTUALLY call the real AI
    private val client by lazy {
        HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = ApiConfig.REQUEST_TIMEOUT_MS
                connectTimeoutMillis = ApiConfig.REQUEST_TIMEOUT_MS
            }
        }
    }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun verifyMedication(
        imageBitmap: Bitmap,
        medicationId: String
    ): VerificationResponse {

        if (ApiConfig.USE_MOCK_AI) {
            return mockVerify(medicationId)
        }

        val base64Image = bitmapToCompressedBase64(imageBitmap)
        val request = VerificationRequest(image = base64Image, medication_id = medicationId)

        var lastError: Exception? = null

        repeat(ApiConfig.MAX_RETRIES + 1) { attempt ->
            try {
                val response = client.post(ApiConfig.AI_VERIFY_URL) {
                    header("Content-Type", "application/json")
                    setBody(json.encodeToString(VerificationRequest.serializer(), request))
                }
                val responseText = response.bodyAsText()
                return json.decodeFromString(VerificationResponse.serializer(), responseText)
            } catch (e: Exception) {
                lastError = e
                Log.e(TAG, "Attempt ${attempt + 1} failed: ${e.message}")
            }
        }

        // All retries exhausted — fail honestly instead of pretending success.
        // The caller (ViewModel) will escalate to Level 3 on verified=false,
        // which is the SAFE default for a medication-safety app.
        Log.e(TAG, "All retries exhausted", lastError)
        return VerificationResponse(
            verified = false,
            message = "Could not reach verification server. Please try again or ask for help."
        )
    }

    private suspend fun mockVerify(medicationId: String): VerificationResponse {
        delay(ApiConfig.MOCK_DELAY_MS)
        Log.d(TAG, "MOCK MODE — no real AI call made for medicationId=$medicationId")
        return VerificationResponse(
            verified = ApiConfig.MOCK_VERIFIED_SUCCESS,  // 🔥 USES THE TOGGLE!
            pillCount = if (ApiConfig.MOCK_VERIFIED_SUCCESS) 1 else 0,
            message = if (ApiConfig.MOCK_VERIFIED_SUCCESS)
                "✅ Mock verification passed (Person 3's AI not connected yet)"
            else
                "❌ Mock verification FAILED: Pill mismatch or expired!"
        )
    }

    /**
     * Resize + compress before sending. Keeps uploads fast and reliable
     * on weak mobile connections, and keeps payload well under typical
     * server body-size limits.
     */
    private fun bitmapToCompressedBase64(bitmap: Bitmap): String {
        val resized = resizeIfNeeded(bitmap, MAX_IMAGE_DIMENSION)
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun resizeIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val largestSide = maxOf(width, height)
        if (largestSide <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / largestSide
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}