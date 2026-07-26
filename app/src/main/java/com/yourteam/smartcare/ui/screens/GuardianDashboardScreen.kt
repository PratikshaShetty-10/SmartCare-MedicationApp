package com.yourteam.smartcare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.yourteam.smartcare.ui.MedicationUiState
import com.yourteam.smartcare.ui.MedicationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianDashboardScreen(
    navController: NavController,
    medicineName: String,
    viewModel: MedicationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guardian Dashboard", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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

                    val takenCount = medications.count { it.status == "Taken" }
                    val totalCount = medications.size
                    val complianceRate = if (totalCount > 0) takenCount.toFloat() / totalCount else 1.0f

                    val missedMedication = medications.firstOrNull { it.status == "Missed" }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. CRITICAL ALERTS SECTION
                        if (missedMedication != null) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDE8E8)),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning, // Changed to explicitly target Icons.Filled
                                            contentDescription = "Alert",
                                            tint = Color.Red,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Active Alerts", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("Missed Afternoon Dosage: ${missedMedication.name} (${missedMedication.time})", color = Color(0xFF9B1C1C), fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 2. ADHERENCE & COMPLIANCE SUMMARY
                        item {
                            Text("Adherence Overview", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Weekly Compliance Rate", fontWeight = FontWeight.Medium)
                                        Text("${(complianceRate * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { complianceRate },
                                        modifier = Modifier.fillMaxWidth().height(8.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }

                        // 3. MEDICATION SCHEDULES
                        item {
                            Text("Today's Schedule", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(medications) { med ->
                            val statusColor = when (med.status) {
                                "Taken" -> Color(0xFF2E7D32)
                                "Missed" -> Color.Red
                                else -> Color.Gray
                            }
                            ScheduleItem(
                                time = med.time,
                                medName = "${med.name} ${med.dosage}",
                                status = med.status,
                                statusColor = statusColor
                            )
                        }

                        // 4. STOCK & INVENTORY LEVELS
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Pill Stock Inventory", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    medications.forEach { med ->
                                        StockItem(
                                            name = "${med.name} ${med.dosage}",
                                            remaining = med.stockLeft,
                                            total = med.totalStock,
                                            isLow = med.stockLeft <= 3
                                        )
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

@Composable
fun ScheduleItem(time: String, medName: String, status: String, statusColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    time,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(medName, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
            Text(
                text = status,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun StockItem(name: String, remaining: Int, total: Int, isLow: Boolean = false) {
    val ratio = if (total > 0) remaining.toFloat() / total else 0.0f
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, fontWeight = FontWeight.Medium)
            Text(
                text = "$remaining / $total left",
                color = if (isLow) Color.Red else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isLow) FontWeight.Bold else FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (isLow) Color.Red else MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}