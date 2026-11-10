package com.yourteam.smartcare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.platform.LocalContext
import com.yourteam.smartcare.escalation.DoseStatus
import com.yourteam.smartcare.escalation.DoseStore
import com.yourteam.smartcare.escalation.EscalationScheduler
import com.yourteam.smartcare.escalation.ScheduledMed
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationTrackerScreen(
    navController: NavController,
    viewModel: MedicationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Daily Medication Tracker",
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
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                .padding(16.dp)
        ) {

            Text(
                text = "Today's Schedule",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            when (val state = uiState) {

                is MedicationUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is MedicationUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error: ${state.message}",
                                color = MaterialTheme.colorScheme.error
                            )

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Button(
                                onClick = {
                                    viewModel.fetchTodaySchedule()
                                }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                is MedicationUiState.Success -> {

                    if (state.medications.isEmpty()) {

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No medications scheduled for today.",
                                fontSize = 16.sp
                            )
                        }

                    } else {

                        LazyColumn(
                            verticalArrangement =
                                Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {

                            items(
                                state.medications,
                                key = {
                                    it.id ?: it.medName
                                }
                            ) { med ->

                                // Local stock state for instant UI update
                                var currentStockLocal by remember(med.id) {
                                    mutableStateOf(
                                        med.currentStock
                                    )
                                }

                                // Read today's occurrence status
                                val doseStatus =
                                    med.id?.let { medicationId ->

                                        val scheduledAtMs =
                                            EscalationScheduler
                                                .scheduledTodayMs(
                                                    ScheduledMed(
                                                        medicationId =
                                                            medicationId,
                                                        medName =
                                                            med.medName,
                                                        dosage =
                                                            med.dosage,
                                                        time =
                                                            med.scheduledTime
                                                    )
                                                )

                                        scheduledAtMs?.let {
                                                scheduledTimeMs ->

                                            val occurrenceId =
                                                DoseStore.occurrenceId(
                                                    medicationId,
                                                    scheduledTimeMs
                                                )

                                            DoseStore.get(
                                                context,
                                                occurrenceId
                                            )?.status
                                                ?: DoseStatus.PENDING

                                        } ?: DoseStatus.PENDING

                                    } ?: DoseStatus.PENDING

                                /*
                                 * Convert backend HH:mm:ss format
                                 * into viewer-friendly 12-hour format.
                                 *
                                 * Examples:
                                 * 08:05:00 -> 8:05 AM
                                 * 12:05:00 -> 12:05 PM
                                 * 14:05:00 -> 2:05 PM
                                 * 16:07:00 -> 4:07 PM
                                 * 20:05:00 -> 8:05 PM
                                 */
                                val displayTime = remember(
                                    med.scheduledTime
                                ) {
                                    runCatching {
                                        LocalTime.parse(
                                            med.scheduledTime
                                        ).format(
                                            DateTimeFormatter.ofPattern(
                                                "h:mm a",
                                                Locale.getDefault()
                                            )
                                        )
                                    }.getOrElse {
                                        // Fall back to the original
                                        // value if parsing fails.
                                        med.scheduledTime
                                    }
                                }

                                Card(
                                    modifier =
                                        Modifier.fillMaxWidth(),
                                    shape =
                                        RoundedCornerShape(12.dp),
                                    colors =
                                        CardDefaults.cardColors(
                                            containerColor =
                                                MaterialTheme
                                                    .colorScheme
                                                    .surfaceVariant
                                        )
                                ) {

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment =
                                            Alignment.CenterVertically,
                                        horizontalArrangement =
                                            Arrangement.SpaceBetween
                                    ) {

                                        Column {

                                            Text(
                                                text = med.medName,
                                                fontSize = 18.sp,
                                                fontWeight =
                                                    FontWeight.Bold
                                            )

                                            Text(
                                                text =
                                                    "${med.dosage} • $displayTime",
                                                fontSize = 14.sp,
                                                color =
                                                    MaterialTheme
                                                        .colorScheme
                                                        .onSurfaceVariant
                                            )

                                            Text(
                                                text =
                                                    "Stock: $currentStockLocal left",
                                                fontSize = 12.sp,
                                                color =
                                                    MaterialTheme
                                                        .colorScheme
                                                        .outline
                                            )
                                        }

                                        // Show action based on today's dose status
                                        when (doseStatus) {

                                            DoseStatus.TAKEN -> {
                                                Row(
                                                    verticalAlignment =
                                                        Alignment
                                                            .CenterVertically
                                                ) {

                                                    Icon(
                                                        imageVector =
                                                            Icons.Default
                                                                .CheckCircle,
                                                        contentDescription =
                                                            "Taken",
                                                        tint =
                                                            Color(
                                                                0xFF2E7D32
                                                            )
                                                    )

                                                    Spacer(
                                                        modifier =
                                                            Modifier.width(
                                                                4.dp
                                                            )
                                                    )

                                                    Text(
                                                        text = "Taken",
                                                        color =
                                                            Color(
                                                                0xFF2E7D32
                                                            ),
                                                        fontWeight =
                                                            FontWeight.Bold,
                                                        fontSize =
                                                            14.sp
                                                    )
                                                }
                                            }

                                            DoseStatus.MISSED -> {
                                                Text(
                                                    text = "Missed",
                                                    color =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .error,
                                                    fontWeight =
                                                        FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }

                                            else -> {
                                                IconButton(
                                                    onClick = {
                                                        if (med.id != null) {
                                                            currentStockLocal -= 1

                                                            viewModel.markAsTaken(
                                                                med.id,
                                                                context
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector =
                                                            Icons.Default
                                                                .RadioButtonUnchecked,
                                                        contentDescription =
                                                            "Mark Taken",
                                                        tint =
                                                            MaterialTheme
                                                                .colorScheme
                                                                .primary
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
        }
    }
}