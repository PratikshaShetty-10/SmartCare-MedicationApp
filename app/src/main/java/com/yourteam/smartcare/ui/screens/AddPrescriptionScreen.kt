package com.yourteam.smartcare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.yourteam.smartcare.ui.MedicationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPrescriptionScreen(
    navController: NavController,
    onScanClick: () -> Unit,
    viewModel: MedicationViewModel = viewModel()
) {
    val context = LocalContext.current
    var medName by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var timeSlot by remember { mutableStateOf("08:00:00") }
    var totalStock by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Prescription", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                onClick = onScanClick,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Auto-fill via Label Scan", fontWeight = FontWeight.Bold)
                        Text("Use camera to read bottle text", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text(
                text = "Prescription Details",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = medName,
                onValueChange = { medName = it },
                label = { Text("Medication Name") },
                leadingIcon = { Icon(Icons.Default.Vaccines, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text("Dosage (e.g. 500mg, 1 Tablet)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = timeSlot,
                onValueChange = { timeSlot = it },
                label = { Text("Schedule Time (e.g. 08:00:00)") },
                leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = totalStock,
                onValueChange = { totalStock = it },
                label = { Text("Initial Stock / Pill Count") },
                leadingIcon = { Icon(Icons.Default.Inventory, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.addMedication(
                        medName = medName.trim(),
                        dosage = dosage.trim(),
                        scheduledTime = timeSlot.trim(),
                        currentStock = totalStock.toIntOrNull() ?: 0,
                        context = context
                    ) {
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = medName.isNotBlank() && dosage.isNotBlank() && totalStock.isNotBlank()
            ) {
                Text("Save Prescription", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}