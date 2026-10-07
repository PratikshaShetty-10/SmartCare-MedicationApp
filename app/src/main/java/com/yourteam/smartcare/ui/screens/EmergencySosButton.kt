package com.yourteam.smartcare.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.yourteam.smartcare.api.GuardianNotifier
import com.yourteam.smartcare.ui.screens.SosViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Intent
import com.yourteam.smartcare.notifications.services.SosService

@Composable
fun EmergencySosButton(
    modifier: Modifier = Modifier,
    viewModel: SosViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Silently fetch the Guardian ID so the button enables itself
    LaunchedEffect(Unit) {
        viewModel.fetchEmergencyData()
    }

    val guardianNotifier = remember { GuardianNotifier(context) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val guardianId by viewModel.guardianId.collectAsState()
    val statusText by viewModel.statusText.collectAsState()

    var buttonState by remember { mutableStateOf(SosButtonState.IDLE) }

    // 2. The core logic: Firebase Push + SMS + Location
    fun sendSilentSosToGuardian() {
        buttonState = SosButtonState.SENDING

        // 🚨 Start the local SOS siren + popup
        try {
            val sosIntent = Intent(context, SosService::class.java)
            ContextCompat.startForegroundService(context, sosIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Keep the existing Firebase Push Notification
        viewModel.triggerSosAlert(context)

        // Keep your existing SMS / GPS logic running at the same time
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                val lat = location?.latitude ?: 0.0
                val lng = location?.longitude ?: 0.0

                coroutineScope.launch {
                    try {
                        guardianId?.let { id ->
                            guardianNotifier.notifySOS(id, lat, lng)

                            buttonState = SosButtonState.SENT
                            delay(3000)
                            buttonState = SosButtonState.IDLE
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        buttonState = SosButtonState.IDLE
                    }
                }
            }.addOnFailureListener {
                buttonState = SosButtonState.IDLE
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            buttonState = SosButtonState.IDLE
        }
    }

    // 3. Permission handler (Checks BOTH Location and SMS)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val smsGranted = permissions[Manifest.permission.SEND_SMS] == true

        if (locGranted && smsGranted) {
            sendSilentSosToGuardian()
        } else {
            buttonState = SosButtonState.IDLE
        }
    }

    // 4. The UI Button
    Column(modifier = modifier.fillMaxWidth()) {

        if (guardianId == null && statusText.isNotBlank()) {
            Text(
                text = statusText,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                if (buttonState != SosButtonState.IDLE) return@Button

                val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

                if (hasLoc && hasSms) {
                    sendSilentSosToGuardian()
                } else {
                    val permissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.SEND_SMS
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = when (buttonState) {
                    SosButtonState.IDLE -> MaterialTheme.colorScheme.error
                    SosButtonState.SENDING -> MaterialTheme.colorScheme.tertiary
                    SosButtonState.SENT -> MaterialTheme.colorScheme.primary
                }
            ),
            enabled = guardianId != null
        ) {
            val buttonText = when {
                guardianId == null -> "LOCATING GUARDIAN..."
                buttonState == SosButtonState.SENDING -> "⏳ ALERTING GUARDIAN..."
                buttonState == SosButtonState.SENT -> "✅ GUARDIAN ALERTED!"
                else -> "🚨 SEND SOS TO GUARDIAN 🚨"
            }

            Text(buttonText, fontSize = 20.sp, color = MaterialTheme.colorScheme.onError)
        }
    }
}

enum class SosButtonState {
    IDLE, SENDING, SENT
}