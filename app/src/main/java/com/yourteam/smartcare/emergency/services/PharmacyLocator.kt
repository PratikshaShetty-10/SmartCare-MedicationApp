package com.yourteam.smartcare.emergency.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.android.gms.location.LocationServices

object PharmacyLocator {

    fun openNearbyPharmacies(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val mapIntent = if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude
                    val gmmIntentUri = Uri.parse("geo:$lat,$lng?q=pharmacy")
                    Intent(Intent.ACTION_VIEW, gmmIntentUri).setPackage("com.google.android.apps.maps")
                } else {
                    val gmmIntentUri = Uri.parse("geo:0,0?q=pharmacy")
                    Intent(Intent.ACTION_VIEW, gmmIntentUri).setPackage("com.google.android.apps.maps")
                }

                try {
                    context.startActivity(mapIntent)
                } catch (e: Exception) {
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/pharmacy/")
                    )
                    context.startActivity(webIntent)
                }
            }.addOnFailureListener {
                val fallbackIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=pharmacy")
                ).setPackage("com.google.android.apps.maps")

                try {
                    context.startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    Toast.makeText(context, "Could not open map application.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Location permission is required to find nearby stores.", Toast.LENGTH_LONG).show()
        }
    }
}