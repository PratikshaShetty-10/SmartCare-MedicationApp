package com.yourteam.smartcare.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yourteam.smartcare.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

@Serializable
data class LocationIQResponse(val display_name: String)

class LocationHelper(private val context: Context) {
    private val TAG = "LocationHelper"
    private val client = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ Permission not granted")
            continuation.resume(null); return@suspendCancellableCoroutine
        }
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener {
                Log.d(TAG, "📍 Location: ${it?.latitude}, ${it?.longitude}")
                continuation.resume(it)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to get location", e)
                continuation.resume(null)
            }
    }

    suspend fun getAddressFromCoordinates(lat: Double, lng: Double): String {
        return try {
            val response: LocationIQResponse = client.get("https://us1.locationiq.com/v1/reverse") {
                parameter("key", BuildConfig.LOCATIONIQ_API_KEY)
                parameter("lat", lat)
                parameter("lon", lng)
                parameter("format", "json")
            }.body()
            Log.d(TAG, "📍 Resolved address: ${response.display_name}")
            response.display_name
        } catch (e: Exception) {
            Log.e(TAG, "❌ Geocoding failed", e)
            "Address unavailable"
        }
    }
}