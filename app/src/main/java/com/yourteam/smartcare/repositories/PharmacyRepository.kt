package com.yourteam.smartcare.repositories

import com.yourteam.smartcare.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.* // Added for cleaner JSON
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.* // Added for cleaner JSON
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LocationIQResponse(
    val name: String,
    val display_name: String,
    val lat: String,
    val lon: String
)

class PharmacyRepository {
    // 1. Singleton pattern: Reuse the client across the app
    companion object {
        private val client = HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private val ACCESS_TOKEN = BuildConfig.LOCATIONIQ_API_KEY

    suspend fun findNearbyPharmacies(lat: Double, lng: Double): List<LocationIQResponse> {
        return try {
            val response = client.get("https://us1.locationiq.com/v1/nearby") {
                parameter("key", ACCESS_TOKEN)
                parameter("lat", lat)
                parameter("lon", lng)
                parameter("tag", "pharmacy")
                parameter("format", "json")
            }

            if (response.status.value in 200..299) {
                // Using Ktor's built-in body serialization instead of manual parsing
                response.body()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            // Log the error so you can see it in Logcat during development
            e.printStackTrace()
            emptyList()
        }
    }
}