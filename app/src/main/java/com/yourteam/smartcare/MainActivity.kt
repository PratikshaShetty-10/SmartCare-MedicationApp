package com.yourteam.smartcare

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yourteam.smartcare.emergency.services.EscalationManager
import com.yourteam.smartcare.emergency.services.EscalationReceiver
import com.yourteam.smartcare.emergency.services.PharmacyLocator
import com.yourteam.smartcare.notifications.services.SosService
import com.yourteam.smartcare.ui.verification.CameraImplementation
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class MedicationRow(
    val id: String,
    val name: String
)

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }

        if (locationGranted && notificationGranted) {
            Toast.makeText(this, "Permissions granted successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions are required for emergency alerts & tracking.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            var showCameraTest by remember { mutableStateOf(false) }
            var activeMedicationId by remember { mutableStateOf<String?>(null) }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                if (showCameraTest && activeMedicationId != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraImplementation(
                            medicationId = activeMedicationId!!,
                            onVerificationComplete = { success ->
                                Toast.makeText(this@MainActivity, "Verification Result: $success", Toast.LENGTH_LONG).show()
                                showCameraTest = false
                            }
                        )
                        Button(
                            onClick = { showCameraTest = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        ) {
                            Text("Back")
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SmartCare Test Console",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // 1. Test SOS Panic Alarm & Foreground Service
                        Button(
                            onClick = {
                                Toast.makeText(this@MainActivity, "Triggering Emergency SOS...", Toast.LENGTH_SHORT).show()
                                val sosIntent = Intent(this@MainActivity, SosService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    startForegroundService(sosIntent)
                                } else {
                                    startService(sosIntent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(50.dp)
                        ) {
                            Text("🚨 Test: SOS Panic Alarm")
                        }

                        // 2. Test Nearby Pharmacy Locator
                        Button(
                            onClick = {
                                PharmacyLocator.openNearbyPharmacies(this@MainActivity)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(50.dp)
                        ) {
                            Text("📍 Test: Find Nearby Pharmacy")
                        }

                        // 3. Test Stock Notification Button
                        Button(
                            onClick = {
                                Toast.makeText(this@MainActivity, "Testing Stock Notification...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(50.dp)
                        ) {
                            Text("Test: Stock Notification")
                        }

                        // 4. Test Audio-Safe Exact Alarm Escalation Service (Demo Mode)
                        Button(
                            onClick = {
                                Toast.makeText(this@MainActivity, "Starting Escalation Demo Sequence...", Toast.LENGTH_SHORT).show()

                                val intent = Intent(this@MainActivity, EscalationReceiver::class.java).apply {
                                    putExtra("MED_NAME", "Paracetamol")
                                    putExtra("GUARDIAN_ID", "sample_guardian_id")
                                }

                                val pendingIntent = PendingIntent.getBroadcast(
                                    this@MainActivity, 0, intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )

                                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                                // Fires almost instantly (2 seconds from now) to kick off the 1-min interval demo
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    if (alarmManager.canScheduleExactAlarms()) {
                                        alarmManager.setExactAndAllowWhileIdle(
                                            AlarmManager.RTC_WAKEUP,
                                            System.currentTimeMillis() + 2000,
                                            pendingIntent
                                        )
                                    } else {
                                        // Fallback if exact permission isn't granted yet
                                        alarmManager.setAndAllowWhileIdle(
                                            AlarmManager.RTC_WAKEUP,
                                            System.currentTimeMillis() + 2000,
                                            pendingIntent
                                        )
                                    }
                                } else {
                                    alarmManager.setExactAndAllowWhileIdle(
                                        AlarmManager.RTC_WAKEUP,
                                        System.currentTimeMillis() + 2000,
                                        pendingIntent
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(50.dp)
                        ) {
                            Text("Test: Real Escalation Service")
                        }

                        // 5. Test Camera Verification UI Component with dynamic fetch
                        Button(
                            onClick = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        val user = SmartCareApp.supabase.auth.currentUserOrNull()
                                        val meds = SmartCareApp.supabase.from("medications")
                                            .select {
                                                if (user != null) {
                                                    filter { eq("user_id", user.id) }
                                                }
                                            }
                                            .decodeList<MedicationRow>()

                                        val targetId = meds.firstOrNull()?.id ?: "fallback_med_id"

                                        CoroutineScope(Dispatchers.Main).launch {
                                            activeMedicationId = targetId
                                            showCameraTest = true
                                        }
                                    } catch (e: Exception) {
                                        CoroutineScope(Dispatchers.Main).launch {
                                            activeMedicationId = "fallback_med_id"
                                            showCameraTest = true
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(50.dp)
                        ) {
                            Text("Test: Camera Verification UI")
                        }

                        // 6. Test Stop All Services Button (Stops SOS siren + escalation workers)
                        Button(
                            onClick = {
                                val escalationManager = EscalationManager(this@MainActivity)
                                escalationManager.stopAll()

                                // Also explicitly stop the SOS alarm service if running
                                val stopSosIntent = Intent(this@MainActivity, SosService::class.java).apply {
                                    action = SosService.ACTION_STOP_SOS
                                }
                                startService(stopSosIntent)

                                Toast.makeText(this@MainActivity, "Stopped all background listeners & services.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(50.dp)
                        ) {
                            Text("Test: Stop All Services")
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}