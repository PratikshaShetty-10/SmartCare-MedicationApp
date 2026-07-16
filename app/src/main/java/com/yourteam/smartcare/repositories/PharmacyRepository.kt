package com.yourteam.smartcare.repositories

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Data models for Overpass API response.
 * We use ignoreUnknownKeys = true in your HttpClient configuration
 * to handle the expansive metadata provided by OpenStreetMap.
 */
@Serializable
data class OverpassResponse(
    val elements: List<Pharmacy> = emptyList()
)

@Serializable
data class Pharmacy(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String> = emptyMap()
) {
    // Computed property for UI convenience
    val name: String get() = tags["name"] ?: "Unnamed Pharmacy"
    val address: String get() = tags["addr:full"] ?: tags["addr:street"] ?: "Address not available"
}

class PharmacyRepository(private val client: HttpClient) {

    /**
     * Finds nearby pharmacies using the free, public Overpass API.
     * @param lat User's latitude
     * @param lng User's longitude
     * @param radius (Optional) Search range in degrees (0.05 ≈ 5km)
     */
    suspend fun findNearbyPharmacies(lat: Double, lng: Double, radius: Double = 0.05): List<Pharmacy> {
        // Construct the Overpass Query Language (QL) string
        val query = """
            [out:json];
            node["amenity"="pharmacy"](${lat - radius},${lng - radius},${lat + radius},${lng + radius});
            out;
        """.trimIndent()

        return try {
            // Execute the GET request
            val response: OverpassResponse = client.get("https://overpass-api.de/api/interpreter") {
                parameter("data", query)
            }.body()

            response.elements
        } catch (e: Exception) {
            // Log the error and return an empty list to prevent app crashes
            e.printStackTrace()
            emptyList()
        }
    }
}