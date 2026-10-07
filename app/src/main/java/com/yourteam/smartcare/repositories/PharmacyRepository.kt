package com.yourteam.smartcare.repositories

import com.yourteam.smartcare.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class GeoapifyResponse(
    val features: List<GeoapifyFeature> = emptyList()
)

@Serializable
data class GeoapifyFeature(
    val properties: GeoapifyProperties
)

@Serializable
data class GeoapifyProperties(
    val placeId: String? = null,
    val name: String? = null,

    @SerialName("formatted")
    val formattedAddress: String? = null,

    @SerialName("address_line1")
    val addressLine1: String? = null,

    @SerialName("address_line2")
    val addressLine2: String? = null,

    val city: String? = null,
    val postcode: String? = null,
    val country: String? = null,

    val lat: Double? = null,
    val lon: Double? = null,

    @SerialName("opening_hours")
    val openingHours: String? = null
)

data class Pharmacy(
    val id: String,
    val lat: Double,
    val lon: Double,
    val name: String,
    val address: String,
    val openingHours: String? = null
)

class PharmacyRepository {

    private val client = OkHttpClient()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun findNearbyPharmacies(
        lat: Double,
        lng: Double,
        radius: Int = 10000
    ): Result<List<Pharmacy>> {

        val apiKey = BuildConfig.GEOAPIFY_API_KEY

        if (apiKey.isBlank()) {
            val errorMessage = "Geoapify API key is missing"

            println("PHARMACY_API_ERROR: $errorMessage")

            return Result.failure(
                IllegalStateException(errorMessage)
            )
        }

        val url =
            "https://api.geoapify.com/v2/places" +
                    "?categories=healthcare.pharmacy" +
                    "&filter=circle:$lng,$lat,$radius" +
                    "&limit=20" +
                    "&apiKey=$apiKey"

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                response.use {

                    if (!it.isSuccessful) {
                        val errorMessage =
                            "Geoapify API returned HTTP ${it.code}"

                        println("PHARMACY_API_ERROR: $errorMessage")

                        return@withContext Result.failure(
                            Exception(errorMessage)
                        )
                    }

                    val responseBody = it.body?.string()

                    if (responseBody.isNullOrBlank()) {
                        val errorMessage =
                            "Empty response from Geoapify API"

                        println("PHARMACY_API_ERROR: $errorMessage")

                        return@withContext Result.failure(
                            Exception(errorMessage)
                        )
                    }

                    val parsedResponse =
                        json.decodeFromString<GeoapifyResponse>(
                            responseBody
                        )

                    val pharmacies =
                        parsedResponse.features.mapNotNull { feature ->

                            val properties = feature.properties

                            val pharmacyLat = properties.lat
                            val pharmacyLon = properties.lon

                            if (
                                pharmacyLat == null ||
                                pharmacyLon == null
                            ) {
                                return@mapNotNull null
                            }

                            Pharmacy(
                                id = properties.placeId
                                    ?: "${pharmacyLat}_${pharmacyLon}",

                                lat = pharmacyLat,

                                lon = pharmacyLon,

                                name = properties.name
                                    ?: "Unnamed Pharmacy",

                                address = buildAddress(properties),

                                openingHours = properties.openingHours
                            )
                        }

                    println(
                        "PHARMACY_API_SUCCESS: " +
                                "Received ${pharmacies.size} pharmacies"
                    )

                    Result.success(pharmacies)
                }

            } catch (e: Exception) {

                e.printStackTrace()

                println(
                    "PHARMACY_API_EXCEPTION: " +
                            "${e.javaClass.simpleName}: ${e.message}"
                )

                Result.failure(e)
            }
        }
    }

    private fun buildAddress(
        properties: GeoapifyProperties
    ): String {

        // Prefer Geoapify's complete formatted address.
        properties.formattedAddress
            ?.takeIf { it.isNotBlank() }
            ?.let {
                return it
            }

        // Otherwise build the address from individual fields.
        val addressParts = listOfNotNull(
            properties.addressLine1?.takeIf { it.isNotBlank() },
            properties.addressLine2?.takeIf { it.isNotBlank() },
            properties.city?.takeIf { it.isNotBlank() },
            properties.postcode?.takeIf { it.isNotBlank() },
            properties.country?.takeIf { it.isNotBlank() }
        )

        return addressParts
            .joinToString(", ")
            .ifBlank {
                "Address not available"
            }
    }
}