package com.yourteam.smartcare.ui.screens

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
import com.yourteam.smartcare.ui.components.EmergencySosButton // <-- Imports your new component
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientHomeScreen(
    onTakeMedicineClick: () -> Unit,
    onPharmacyClick: () -> Unit,
    onScanLabelClick: () -> Unit,
    onLogoutClick: () -> Unit = {}
    // REMOVED onSosClick parameter from here
) {
    var profile by remember { mutableStateOf<Profile?>(null) }
    var medications by remember { mutableStateOf<List<MedicationModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val user = SupabaseClient.client.auth.currentUserOrNull()
        if (user != null) {
            try {
                profile = SupabaseClient.client.from("profiles")
                    .select { filter { eq("id", user.id) } }
                    .decodeSingleOrNull<Profile>()

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

            // 2. The NEW Self-Contained SOS Button Component
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

            // 4. Today's Schedule
            Text(
                text = "Today's Schedule",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else if (medications.isEmpty()) {
                Text(
                    text = "You are all caught up for today!",
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                medications.forEach { med ->
                    var isTaken by remember { mutableStateOf(false) }

                    PatientScheduleItem(
                        medName = med.medName,
                        dosage = med.dosage,
                        time = med.scheduledTime,
                        isTaken = isTaken,
                        onMarkAsTaken = {
                            isTaken = true
                            scope.launch {
                                // Update Supabase logic here
                            }
                        }
                    )
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