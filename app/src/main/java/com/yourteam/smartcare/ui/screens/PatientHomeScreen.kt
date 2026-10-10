
package com.yourteam.smartcare.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.Profile
import com.yourteam.smartcare.escalation.DoseStatus
import com.yourteam.smartcare.escalation.DoseStore
import com.yourteam.smartcare.escalation.EscalationScheduler
import com.yourteam.smartcare.escalation.ScheduledMed
import com.yourteam.smartcare.ui.MedicationUiState
import com.yourteam.smartcare.ui.MedicationViewModel
import com.yourteam.smartcare.ui.components.EmergencySosButton
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// -----------------------------------------------------------------------------
// SCAN LABEL CHOICE DIALOG
// -----------------------------------------------------------------------------

@Composable
private fun ScanLabelChoiceDialog(
    onWeeklyScan: () -> Unit,
    onExpiryDate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Scan Medicine")
        },
        text = {
            Column {
                Text("Choose what you want to scan.")

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onWeeklyScan,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Weekly Scan")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onExpiryDate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Expiry Date")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// -----------------------------------------------------------------------------
// PATIENT HOME SCREEN
// -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(
    onTakeMedicineClick: () -> Unit,
    onPharmacyClick: () -> Unit,
    onScanLabelClick: () -> Unit,
    onExpiryDateClick: () -> Unit,
    onLogoutClick: () -> Unit = {},
    medViewModel: MedicationViewModel = viewModel()
) {
    val context = LocalContext.current

    var showScanChoiceDialog by remember {
        mutableStateOf(false)
    }

    var profile by remember {
        mutableStateOf<Profile?>(null)
    }

    val uiState by medViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        val user = SupabaseClient.client.auth.currentUserOrNull()

        if (user != null) {
            try {
                // Fetch the Elder's profile.
                profile = SupabaseClient.client
                    .from("profiles")
                    .select {
                        filter {
                            eq("id", user.id)
                        }
                    }
                    .decodeSingleOrNull<Profile>()
            } catch (e: Exception) {
                Log.e("PatientHome", "Failed to fetch profile", e)
            }
        }

        // Fetch today's medication schedule and schedule Android alarms.
        // Existing P6 escalation behavior remains unchanged.
        medViewModel.fetchTodaySchedule(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SmartCare",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onLogoutClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Log Out"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Welcome Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Hello, ${profile?.name ?: "Elder"}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Guardian Connected • Monitored",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. SOS Button — unchanged
            EmergencySosButton()

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    text = "Scan Label",
                    icon = Icons.Default.PhotoCamera,
                    onClick = {
                        showScanChoiceDialog = true
                    },
                    modifier = Modifier.weight(1f)
                )

                QuickActionButton(
                    text = "Pharmacies",
                    icon = Icons.Default.LocalPharmacy,
                    onClick = onPharmacyClick,
                    modifier = Modifier.weight(1f)
                )

                QuickActionButton(
                    text = "Med Log",
                    icon = Icons.Default.Medication,
                    onClick = onTakeMedicineClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 4. Today's Schedule — unchanged
            Text(
                text = "Today's Schedule",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (val state = uiState) {
                is MedicationUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp)
                    )
                }

                is MedicationUiState.Error -> {
                    Text(
                        text = "Error loading schedule: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                is MedicationUiState.Success -> {
                    if (state.medications.isEmpty()) {
                        Text(
                            text = "You are all caught up for today!",
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        state.medications.forEach { med ->
                            val doseStatus = med.id?.let { medicationId ->
                                val scheduledAtMs =
                                    EscalationScheduler.scheduledTodayMs(
                                        ScheduledMed(
                                            medicationId = medicationId,
                                            medName = med.medName,
                                            dosage = med.dosage,
                                            time = med.scheduledTime
                                        )
                                    )

                                scheduledAtMs?.let { scheduledTimeMs ->
                                    val occurrenceId = DoseStore.occurrenceId(
                                        medicationId,
                                        scheduledTimeMs
                                    )

                                    DoseStore.get(
                                        context,
                                        occurrenceId
                                    )?.status ?: DoseStatus.PENDING
                                } ?: DoseStatus.PENDING
                            } ?: DoseStatus.PENDING

                            PatientScheduleItem(
                                medName = med.medName,
                                dosage = med.dosage,
                                time = med.scheduledTime,
                                doseStatus = doseStatus,
                                onMarkAsTaken = {
                                    med.id?.let { medId ->
                                        medViewModel.markAsTaken(
                                            medId,
                                            context
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Display the scan-choice dialog without changing dashboard state or navigation.
    if (showScanChoiceDialog) {
        ScanLabelChoiceDialog(
            onWeeklyScan = {
                showScanChoiceDialog = false
                onScanLabelClick()
            },
            onExpiryDate = {
                showScanChoiceDialog = false
                onExpiryDateClick()
            },
            onDismiss = {
                showScanChoiceDialog = false
            }
        )
    }
}

// -----------------------------------------------------------------------------
// QUICK ACTION BUTTON — unchanged
// -----------------------------------------------------------------------------

@Composable
fun QuickActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// -----------------------------------------------------------------------------
// PATIENT SCHEDULE ITEM — unchanged
// -----------------------------------------------------------------------------

@Composable
fun PatientScheduleItem(
    medName: String,
    dosage: String,
    time: String,
    doseStatus: String,
    onMarkAsTaken: () -> Unit
) {
    // Convert backend HH:mm:ss into a viewer-friendly 12-hour format.
    val displayTime = remember(time) {
        runCatching {
            LocalTime.parse(time).format(
                DateTimeFormatter.ofPattern(
                    "h:mm a",
                    Locale.getDefault()
                )
            )
        }.getOrElse {
            time
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayTime,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "$medName ($dosage)",
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            when (doseStatus) {
                DoseStatus.TAKEN -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Taken",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "Taken",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                DoseStatus.MISSED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Missed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "Missed",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                else -> {
                    OutlinedButton(
                        onClick = onMarkAsTaken,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Text(
                            text = "Take",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
