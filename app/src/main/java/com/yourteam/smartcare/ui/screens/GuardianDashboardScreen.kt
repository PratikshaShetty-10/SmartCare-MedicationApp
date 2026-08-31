package com.yourteam.smartcare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.ui.MedicationUiState
import com.yourteam.smartcare.ui.MedicationViewModel
import com.yourteam.smartcare.ui.navigation.Screen
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianDashboardScreen(
    navController: NavController,
    medicineName: String = "All Prescriptions",
    viewModel: MedicationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // --- NEW: Emergency State & Listener ---
    val activeEmergency by viewModel.activeEmergency.collectAsState()
    val currentUserId = remember { SupabaseClient.client.auth.currentUserOrNull()?.id ?: "" }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            viewModel.listenForEmergencies(currentUserId)
        }
    }

    var medToEdit by remember { mutableStateOf<Medication?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guardian Dashboard", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        CoroutineScope(Dispatchers.IO).launch {
                            SupabaseClient.client.auth.signOut()
                        }
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }) {
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
                onClick = { navController.navigate(Screen.AddPrescription.route) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Prescription")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val state = uiState) {
                is MedicationUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is MedicationUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Error loading metrics: ${state.message}", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.fetchTodaySchedule() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is MedicationUiState.Success -> {
                    val medications = state.medications

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        // --- NEW: THE SOS BANNER ---
                        if (activeEmergency != null) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.Red)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = "SOS", tint = Color.White, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("ACTIVE EMERGENCY!", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Color.White)
                                        Text("The elder has triggered the SOS button.", color = Color.White)
                                        Text("Location: Lat ${activeEmergency!!.lat}, Lng ${activeEmergency!!.lng}", color = Color.White)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { viewModel.dismissEmergency() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                                        ) {
                                            Text("Acknowledge & Dismiss", color = Color.Red, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("Adherence & Active Prescriptions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }

                        if (medications.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text("No prescriptions added yet. Tap + to add one.")
                                    }
                                }
                            }
                        } else {
                            items(medications) { med ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(med.scheduledTime, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("${med.medName} (${med.dosage})", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                            Text("Stock: ${med.currentStock} units", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { medToEdit = med }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = {
                                                med.id?.let { viewModel.deleteMedication(it) }
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (medToEdit != null) {
            var editName by remember { mutableStateOf(medToEdit!!.medName) }
            var editDosage by remember { mutableStateOf(medToEdit!!.dosage) }
            var editTime by remember { mutableStateOf(medToEdit!!.scheduledTime) }
            var editStock by remember { mutableStateOf(medToEdit!!.currentStock.toString()) }

            AlertDialog(
                onDismissRequest = { medToEdit = null },
                title = { Text("Edit Prescription", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Medication Name") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editDosage,
                            onValueChange = { editDosage = it },
                            label = { Text("Dosage") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editTime,
                            onValueChange = { editTime = it },
                            label = { Text("Time (HH:MM:SS)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editStock,
                            onValueChange = { editStock = it },
                            label = { Text("Stock Remaining") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            medToEdit?.id?.let { id ->
                                viewModel.updateMedication(
                                    medicationId = id,
                                    newName = editName.trim(),
                                    newDosage = editDosage.trim(),
                                    newTime = editTime.trim(),
                                    newStock = editStock.toIntOrNull() ?: 0
                                )
                            }
                            medToEdit = null
                        }
                    ) {
                        Text("Save Changes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { medToEdit = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}