package com.yourteam.smartcare.api

import com.yourteam.smartcare.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

@Serializable
data class Pharmacy(
    val name: String,
    val display_name: String,
    val lat: String,
    val lon: String
)

class GooglePlacesClient(private val client: HttpClient) {
    private val apiKey = BuildConfig.LOCATIONIQ_API_KEY

    suspend fun findNearbyPharmacies(lat: Double, lng: Double): List<Pharmacy> {
        return try {
            client.get("https://us1.locationiq.com/v1/nearby") {
                parameter("key", apiKey)
                parameter("lat", lat)
                parameter("lon", lng)
                parameter("tag", "pharmacy")
                parameter("format", "json")
            }.body<List<Pharmacy>>()
        } catch (e: Exception) {
            emptyList()
        }
    }
}