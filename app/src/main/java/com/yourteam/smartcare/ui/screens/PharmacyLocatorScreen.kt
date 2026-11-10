package com.yourteam.smartcare.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.yourteam.smartcare.emergency.services.PharmacyLocationHelper
import com.yourteam.smartcare.emergency.services.PharmacyLocationResult
import com.yourteam.smartcare.repositories.PharmacyRepository
import kotlinx.coroutines.launch
import java.util.Locale

data class PharmacyLocation(
    val id: String,
    val name: String,
    val distance: String,
    val address: String,
    val status: String,
    val latitude: Double,
    val longitude: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PharmacyLocatorScreen(navController: NavController) {

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pharmacyRepository = remember { PharmacyRepository() }

    var locationStatus by remember {
        mutableStateOf("Location permission is required.")
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var pharmacies by remember {
        mutableStateOf<List<PharmacyLocation>>(emptyList())
    }

    fun fetchPharmacies(latitude: Double, longitude: Double) {
        coroutineScope.launch {

            isLoading = true
            locationStatus = "Finding nearby pharmacies..."

            val result = pharmacyRepository.findNearbyPharmacies(
                lat = latitude,
                lng = longitude
            )

            result.onSuccess { pharmacyList ->

                val userLocation =
                    android.location.Location("user").apply {
                        this.latitude = latitude
                        this.longitude = longitude
                    }

                pharmacies = pharmacyList
                    .map { pharmacy ->

                        val pharmacyLocation =
                            android.location.Location("pharmacy").apply {
                                this.latitude = pharmacy.lat
                                this.longitude = pharmacy.lon
                            }

                        val distanceMeters =
                            userLocation.distanceTo(pharmacyLocation)

                        PharmacyLocation(
                            id = pharmacy.id,
                            name = pharmacy.name,
                            distance = formatDistance(distanceMeters),
                            address = pharmacy.address,
                            status = "Pharmacy available",
                            latitude = pharmacy.lat,
                            longitude = pharmacy.lon
                        )
                    }
                    .sortedBy { pharmacy ->
                        distanceInMeters(
                            latitude,
                            longitude,
                            pharmacy.latitude,
                            pharmacy.longitude
                        )
                    }

                locationStatus =
                    if (pharmacies.isEmpty()) {
                        "No pharmacies found nearby."
                    } else {
                        "Found ${pharmacies.size} nearby pharmacies."
                    }
            }

            result.onFailure { error ->

                pharmacies = emptyList()

                locationStatus =
                    when {
                        error.message?.contains(
                            "API key",
                            ignoreCase = true
                        ) == true -> {
                            "Pharmacy service is not configured."
                        }

                        else -> {
                            "Unable to load nearby pharmacies. Please check your internet connection."
                        }
                    }
            }

            isLoading = false
        }
    }

    fun fetchCurrentLocation() {
        coroutineScope.launch {

            isLoading = true
            locationStatus = "Getting your current location..."

            when (
                val result =
                    PharmacyLocationHelper.getCurrentLocation(context)
            ) {

                is PharmacyLocationResult.Success -> {

                    locationStatus =
                        "Current location: ${result.latitude}, ${result.longitude}"

                    fetchPharmacies(
                        latitude = result.latitude,
                        longitude = result.longitude
                    )
                }

                PharmacyLocationResult.PermissionDenied -> {

                    locationStatus =
                        "Location permission is required."

                    isLoading = false
                }

                PharmacyLocationResult.LocationUnavailable -> {

                    locationStatus =
                        "Unable to get your current location. Please check that Location is enabled."

                    isLoading = false
                }
            }
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val locationGranted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (locationGranted) {
                fetchCurrentLocation()
            } else {
                locationStatus =
                    "Location permission was denied."

                isLoading = false
            }
        }

    LaunchedEffect(Unit) {

        val fineGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {

            fetchCurrentLocation()

        } else {

            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        topBar = {

            TopAppBar(
                title = {
                    Text(
                        "Find Nearby Pharmacy",
                        fontWeight = FontWeight.Bold
                    )
                },

                navigationIcon = {

                    IconButton(
                        onClick = {
                            navController.popBackStack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Home"
                        )
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor =
                        MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor =
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            OutlinedTextField(
                value = "",
                onValueChange = {},
                enabled = false,

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

                placeholder = {
                    Text("Using your current location")
                },

                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },

                shape = RoundedCornerShape(12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant
                    ),

                contentAlignment = Alignment.Center
            ) {

                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            if (isLoading) {
                                "Finding nearby pharmacies..."
                            } else {
                                locationStatus
                            },

                        fontWeight = FontWeight.Bold,

                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            if (!isLoading && pharmacies.isEmpty()) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),

                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = locationStatus,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            } else {

                LazyColumn(
                    contentPadding =
                        PaddingValues(16.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(12.dp),

                    modifier =
                        Modifier.fillMaxSize()
                ) {

                    items(
                        items = pharmacies,
                        key = { it.id }
                    ) { pharmacy ->

                        PharmacyRowItem(
                            pharmacy = pharmacy
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PharmacyRowItem(
    pharmacy: PharmacyLocation
) {

    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),

        shape = RoundedCornerShape(12.dp),

        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface
        ),

        elevation =
            CardDefaults.cardElevation(2.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(8.dp)
                    ),

                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    imageVector =
                        Icons.Default.LocalPharmacy,

                    contentDescription = null,

                    tint =
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(
                modifier = Modifier.width(16.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = pharmacy.name,

                    fontSize = 16.sp,

                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                        "${pharmacy.distance} • ${pharmacy.address}",

                    fontSize = 13.sp,

                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = pharmacy.status,

                    fontSize = 13.sp,

                    fontWeight = FontWeight.Bold,

                    color =
                        MaterialTheme.colorScheme.primary
                )
            }

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Button(
                onClick = {

                    openNavigation(
                        context = context,
                        latitude = pharmacy.latitude,
                        longitude = pharmacy.longitude
                    )
                },

                contentPadding =
                    PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    ),

                shape =
                    RoundedCornerShape(8.dp)
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.Navigation,

                        contentDescription =
                            "Navigate",

                        modifier =
                            Modifier.size(16.dp)
                    )

                    Spacer(
                        modifier = Modifier.width(4.dp)
                    )

                    Text(
                        "Go",

                        fontSize = 14.sp,

                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun openNavigation(
    context: android.content.Context,
    latitude: Double,
    longitude: Double
) {

    val navigationUri =
        Uri.parse(
            "google.navigation:q=$latitude,$longitude"
        )

    val navigationIntent =
        Intent(
            Intent.ACTION_VIEW,
            navigationUri
        )

    try {

        context.startActivity(
            navigationIntent
        )

    } catch (
        e: ActivityNotFoundException
    ) {

        val mapUri =
            Uri.parse(
                "geo:$latitude,$longitude?q=$latitude,$longitude"
            )

        val mapIntent =
            Intent(
                Intent.ACTION_VIEW,
                mapUri
            )

        try {

            context.startActivity(
                mapIntent
            )

        } catch (
            e: ActivityNotFoundException
        ) {

            // No map application is available.
        }
    }
}

private fun formatDistance(
    distanceMeters: Float
): String {

    return if (distanceMeters < 1000f) {

        "${distanceMeters.toInt()} m away"

    } else {

        String.format(
            Locale.US,
            "%.1f km away",
            distanceMeters / 1000f
        )
    }
}

private fun distanceInMeters(
    userLatitude: Double,
    userLongitude: Double,
    pharmacyLatitude: Double,
    pharmacyLongitude: Double
): Float {

    val userLocation =
        android.location.Location("user").apply {
            latitude = userLatitude
            longitude = userLongitude
        }

    val pharmacyLocation =
        android.location.Location("pharmacy").apply {
            latitude = pharmacyLatitude
            longitude = pharmacyLongitude
        }

    return userLocation.distanceTo(
        pharmacyLocation
    )
}