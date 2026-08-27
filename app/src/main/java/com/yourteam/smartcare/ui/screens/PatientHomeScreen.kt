package com.yourteam.smartcare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.Medication as MedicationModel
import com.yourteam.smartcare.data.model.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(
    onTakeMedicineClick: () -> Unit,
    onSosClick: () -> Unit,
    onPharmacyClick: () -> Unit,
    onScanLabelClick: () -> Unit,
    onLogoutClick: () -> Unit = {}
) {
    var profile by remember { mutableStateOf<Profile?>(null) }
    var medications by remember { mutableStateOf<List<MedicationModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Fetch logged-in user profile and their linked medications from Supabase
    LaunchedEffect(Unit) {
        val user = SupabaseClient.client.auth.currentUserOrNull()
        if (user != null) {
            try {
                // Fetch profile
                profile = SupabaseClient.client.from("profiles")
                    .select { filter { eq("id", user.id) } }
                    .decodeSingleOrNull<Profile>()

                // Fetch real medications for this elder user
                medications = SupabaseClient.client.from("medications")
                    .select { filter { eq("elderId", user.id) } }
                    .decodeList<MedicationModel>()
            } catch (_: Exception) {}
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartCare Home", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = onLogoutClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Log Out",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScanLabelClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Scan Label Icon"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Label", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Linked Guardian Status Card (Replaces the obsolete Pairing Code section)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Welcome, ${profile?.name ?: "Elder"}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Status: Connected to Guardian",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Your guardian can monitor and manage your medication schedule.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. EMERGENCY SOS BUTTON
            Button(
                onClick = onSosClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "SOS", modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("EMERGENCY SOS", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. QUICK ACTIONS ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onTakeMedicineClick,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Medication, contentDescription = "Meds")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Full Log", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onPharmacyClick,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LocalPharmacy, contentDescription = "Pharmacy")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pharmacies", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. TODAY'S SCHEDULE SECTION
            Text(
                text = "Today's Schedule",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else if (medications.isEmpty()) {
                Text(
                    text = "No medications scheduled for today.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                medications.forEach { med ->
                    var isTaken by remember { mutableStateOf(false) }

                    PatientScheduleItem(
                        medName = med.medName,
                        dosage = med.dosage,
                        time = med.scheduledTime,
                        status = if (isTaken) "Taken" else "Pending",
                        onMarkAsTaken = {
                            isTaken = true
                            scope.launch {
                                try {
                                    // Optional: update pill count/status in Supabase
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PatientScheduleItem(
    medName: String,
    dosage: String,
    time: String,
    status: String,
    onMarkAsTaken: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$medName ($dosage)",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }

            if (status == "Taken") {
                Text(
                    text = "✓ Taken",
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(Color(0xFF2E7D32).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            } else {
                Button(
                    onClick = onMarkAsTaken,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "Mark as Taken", fontSize = 13.sp)
                }
            }
        }
    }
}