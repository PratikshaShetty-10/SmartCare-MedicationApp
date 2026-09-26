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
import com.yourteam.smartcare.ui.components.EmergencySosButton
import com.yourteam.smartcare.ui.MedicationViewModel
import com.yourteam.smartcare.ui.MedicationUiState
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(
    onTakeMedicineClick: () -> Unit,
    onPharmacyClick: () -> Unit,
    onScanLabelClick: () -> Unit,
    onLogoutClick: () -> Unit = {},
    medViewModel: MedicationViewModel = viewModel()
) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<Profile?>(null) }
    var overdoseWarning by remember { mutableStateOf(false) }

    val uiState by medViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        val user = SupabaseClient.client.auth.currentUserOrNull()
        if (user != null) {
            try {
                // 1. Fetch Profile
                profile = SupabaseClient.client.from("profiles")
                    .select { filter { eq("id", user.id) } }
                    .decodeSingleOrNull<Profile>()

                // 2. Fetch Recent Overdoses / Mismatches via medication relation & adherence_logs status
                val userMeds = SupabaseClient.client.from("medications")
                    .select { filter { eq("elder_id", user.id) } }
                    .decodeList<com.yourteam.smartcare.data.model.Medication>()

                val medIds = userMeds.mapNotNull { it.id }

                if (medIds.isNotEmpty()) {
                    val overdoseLogs = SupabaseClient.client.from("adherence_logs")
                        .select {
                            filter {
                                isIn("medication_id", medIds)
                                eq("status", "overdose_flag")
                            }
                        }.data

                    if (overdoseLogs != "[]" && overdoseLogs.isNotEmpty()) {
                        overdoseWarning = true
                    }
                }
            } catch (e: Exception) {
                Log.e("PatientHome", "Failed to fetch profile/logs", e)
            }
        }

        // 3. Fetch Schedule & Schedule Android Alarms
        medViewModel.fetchTodaySchedule(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartCare", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogoutClick) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Log Out")
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

            // 2. Overdose Warning Banner
            if (overdoseWarning) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Medication Mismatch Detected",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Your last pill scan did not match your schedule. Guardian notified.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // 3. The Self-Contained SOS Button Component
            EmergencySosButton()

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    text = "Scan Label",
                    icon = Icons.Default.PhotoCamera,
                    onClick = onScanLabelClick,
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

            // 5. Today's Schedule
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
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
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
                            PatientScheduleItem(
                                medName = med.medName,
                                dosage = med.dosage,
                                time = med.scheduledTime,
                                isTaken = med.isTaken,
                                onMarkAsTaken = {
                                    med.id?.let { medId ->
                                        medViewModel.markAsTaken(medId, context)
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
}

// Custom button for the 3-grid layout
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
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = text, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// Lightened Schedule Item
@Composable
fun PatientScheduleItem(
    medName: String,
    dosage: String,
    time: String,
    isTaken: Boolean,
    onMarkAsTaken: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = time,
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

            if (isTaken) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Taken",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Taken", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            } else {
                OutlinedButton(
                    onClick = onMarkAsTaken,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Take", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}