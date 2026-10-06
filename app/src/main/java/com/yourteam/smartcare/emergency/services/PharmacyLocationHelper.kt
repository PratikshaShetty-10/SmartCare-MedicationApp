package com.yourteam.smartcare.emergency.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed class PharmacyLocationResult {
    data class Success(
        val latitude: Double,
        val longitude: Double
    ) : PharmacyLocationResult()

    data object PermissionDenied : PharmacyLocationResult()

    data object LocationUnavailable : PharmacyLocationResult()
}

object PharmacyLocationHelper {

    suspend fun getCurrentLocation(
        context: Context
    ): PharmacyLocationResult {

        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            return PharmacyLocationResult.PermissionDenied
        }

        val fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(context)

        return suspendCancellableCoroutine { continuation ->

            try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).addOnSuccessListener { location ->

                    if (location != null) {
                        continuation.resume(
                            PharmacyLocationResult.Success(
                                latitude = location.latitude,
                                longitude = location.longitude
                            )
                        )
                    } else {
                        continuation.resume(
                            PharmacyLocationResult.LocationUnavailable
                        )
                    }
                }.addOnFailureListener {
                    continuation.resume(
                        PharmacyLocationResult.LocationUnavailable
                    )
                }
            } catch (e: SecurityException) {
                continuation.resume(
                    PharmacyLocationResult.PermissionDenied
                )
            }
        }
    }
}