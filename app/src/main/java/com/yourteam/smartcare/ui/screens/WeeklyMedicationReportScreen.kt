package com.yourteam.smartcare.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.yourteam.smartcare.data.model.AdherenceLog
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.data.model.VerificationLog
import com.yourteam.smartcare.ui.AdherenceReportUiState
import com.yourteam.smartcare.ui.MedicationUiState
import com.yourteam.smartcare.ui.MedicationViewModel
import com.yourteam.smartcare.ui.VerificationReportUiState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val ReportCardShape = RoundedCornerShape(16.dp)

@Composable
fun WeeklyMedicationReportScreen(
    navController: NavHostController,
    viewModel: MedicationViewModel
) {
    val medicationState by viewModel.uiState.collectAsState()
    val adherenceState by viewModel.adherenceReportState.collectAsState()
    val verificationState by viewModel.verificationReportState.collectAsState()

    var weekStart by remember {
        mutableStateOf(
            LocalDate.now().with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
            )
        )
    }

    var selectedMedicationId by remember {
        mutableStateOf<String?>(null)
    }

    var medicationMenuExpanded by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        viewModel.fetchTodaySchedule()
        viewModel.loadAdherenceReport()
        viewModel.loadVerificationLogs()
    }

    val medications = (medicationState as? MedicationUiState.Success)
        ?.medications
        .orEmpty()
        .filter { it.id != null }

    LaunchedEffect(medications) {
        if (
            selectedMedicationId == null ||
            medications.none { it.id == selectedMedicationId }
        ) {
            selectedMedicationId = medications.firstOrNull()?.id
        }
    }

    val selectedMedication = medications.firstOrNull {
        it.id == selectedMedicationId
    } ?: medications.firstOrNull()

    val weekEndExclusive = weekStart.plusWeeks(1)

    val formatter = remember {
        DateTimeFormatter.ofPattern(
            "dd MMM yyyy",
            Locale.getDefault()
        )
    }

    val adherenceLogs =
        (adherenceState as? AdherenceReportUiState.Success)
            ?.logs
            .orEmpty()

    val verificationLogs =
        (verificationState as? VerificationReportUiState.Success)
            ?.logs
            .orEmpty()

    val medicationId = selectedMedication?.id

    val weeklyAdherence = adherenceLogs.filter { log ->
        log.medicationId == medicationId &&
                isWithinWeek(
                    timestamp = log.scheduledAt,
                    start = weekStart,
                    endExclusive = weekEndExclusive
                )
    }

    val weeklyScans = verificationLogs
        .filter { log ->
            log.medicationId == medicationId &&
                    log.verifiedAt != null &&
                    isWithinWeek(
                        timestamp = log.verifiedAt,
                        start = weekStart,
                        endExclusive = weekEndExclusive
                    )
        }
        .sortedByDescending {
            parseDateTime(it.verifiedAt)
        }

    val takenCount = weeklyAdherence.count {
        it.status.equals("taken", ignoreCase = true)
    }

    val missedCount = weeklyAdherence.count {
        it.status.equals("missed", ignoreCase = true)
    }

    val pendingCount = weeklyAdherence.count {
        it.status.equals("pending", ignoreCase = true)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Page heading
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { navController.popBackStack() }
                    ) {
                        Text("‹ Back")
                    }

                    TextButton(
                        onClick = {
                            viewModel.loadAdherenceReport()
                            viewModel.loadVerificationLogs()
                        }
                    ) {
                        Text("Refresh")
                    }
                }

                Text(
                    text = "Weekly Medication Report",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Medication activity and strip scan history",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Week and medication selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ReportCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 1.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "REPORT PERIOD",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${weekStart.format(formatter)} – " +
                                "${weekEndExclusive.minusDays(1).format(formatter)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                weekStart = weekStart.minusWeeks(1)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("‹ Previous")
                        }

                        OutlinedButton(
                            onClick = {
                                weekStart = weekStart.plusWeeks(1)
                            },
                            enabled = weekEndExclusive.isBefore(
                                LocalDate.now().plusDays(1)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Next ›")
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = "MEDICATION",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    if (medications.isEmpty()) {
                        when (medicationState) {
                            is MedicationUiState.Loading -> {
                                CircularProgressIndicator()
                            }

                            is MedicationUiState.Error -> {
                                Text(
                                    text = (medicationState as MedicationUiState.Error)
                                        .message,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            else -> {
                                Text("No medications available.")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                medicationMenuExpanded = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = selectedMedication?.medName
                                    ?: "Choose medication"
                            )
                        }

                        DropdownMenu(
                            expanded = medicationMenuExpanded,
                            onDismissRequest = {
                                medicationMenuExpanded = false
                            }
                        ) {
                            medications.forEach { medication ->
                                DropdownMenuItem(
                                    text = {
                                        Text(medication.medName)
                                    },
                                    onClick = {
                                        selectedMedicationId = medication.id
                                        medicationMenuExpanded = false
                                    }
                                )
                            }
                        }

                        selectedMedication?.let { medication ->
                            Text(
                                text = "Dosage: ${medication.dosage}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Adherence summary
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Dose adherence",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Scheduled doses for the selected week",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (adherenceState) {
                is AdherenceReportUiState.Loading -> {
                    ReportLoading()
                }

                is AdherenceReportUiState.Error -> {
                    ReportError(
                        (adherenceState as AdherenceReportUiState.Error).message
                    )
                }

                is AdherenceReportUiState.Success -> {
                    if (selectedMedication == null) {
                        Text("Select a medication to view its dose records.")
                    } else if (weeklyAdherence.isEmpty()) {
                        ReportEmptyState(
                            message = "No dose records found for this medication this week."
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CountCard(
                                label = "Taken",
                                count = takenCount,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )

                            CountCard(
                                label = "Missed",
                                count = missedCount,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )

                            CountCard(
                                label = "Pending",
                                count = pendingCount,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        weeklyAdherence
                            .sortedBy { parseDateTime(it.scheduledAt) }
                            .forEach { log ->
                                DoseRecordCard(log)
                            }
                    }
                }
            }

            // Strip scan history
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Strip scan history",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "AI estimates and elder-confirmed pocket counts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Pocket condition does not confirm that a dose was taken.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            when (verificationState) {
                is VerificationReportUiState.Loading -> {
                    ReportLoading()
                }

                is VerificationReportUiState.Error -> {
                    ReportError(
                        (verificationState as VerificationReportUiState.Error).message
                    )
                }

                is VerificationReportUiState.Success -> {
                    if (selectedMedication == null) {
                        Text("Select a medication to view its scan history.")
                    } else if (weeklyScans.isEmpty()) {
                        ReportEmptyState(
                            message = "No scan recorded this week."
                        )
                    } else {
                        weeklyScans.forEach { scan ->
                            ScanRecordCard(scan)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CountCard(
    label: String,
    count: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = ReportCardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun DoseRecordCard(log: AdherenceLog) {
    val scheduledTime = formatTimestamp(log.scheduledAt)
    val takenTime = log.takenAt?.let { formatTimestamp(it) }

    val status = log.status.lowercase(Locale.ROOT)

    val statusContainerColor = when (status) {
        "taken" -> MaterialTheme.colorScheme.primaryContainer
        "missed" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val statusTextColor = when (status) {
        "taken" -> MaterialTheme.colorScheme.onPrimaryContainer
        "missed" -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ReportCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = scheduledTime,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                if (takenTime != null) {
                    Text(
                        text = "Taken at $takenTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(1.dp))

            Surface(
                color = statusContainerColor,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = log.status.replaceFirstChar {
                        it.uppercase(Locale.getDefault())
                    },
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 6.dp
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = statusTextColor
                )
            }
        }
    }
}

@Composable
private fun ScanRecordCard(log: VerificationLog) {
    val filled = log.confirmedFilledCount ?: log.aiFilledCount
    val empty = log.confirmedEmptyCount ?: log.aiEmptyCount
    val broken = log.confirmedBrokenCount ?: log.aiBrokenCount

    val hasConfirmedCounts =
        log.confirmedFilledCount != null ||
                log.confirmedEmptyCount != null ||
                log.confirmedBrokenCount != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ReportCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = formatTimestamp(log.verifiedAt),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PocketCount(
                    label = "Filled",
                    count = filled,
                    modifier = Modifier.weight(1f)
                )

                PocketCount(
                    label = "Empty",
                    count = empty,
                    modifier = Modifier.weight(1f)
                )

                PocketCount(
                    label = "Broken",
                    count = broken,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = if (hasConfirmedCounts) {
                    "Elder-confirmed counts"
                } else if (filled != null || empty != null || broken != null) {
                    "AI-estimated counts"
                } else {
                    "Pocket counts unavailable"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Expected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = log.expectedCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "AI detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = log.actualCountYolo.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Surface(
                color = if (log.mismatchFlag) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (log.mismatchFlag) {
                        "Count mismatch"
                    } else {
                        "Counts match"
                    },
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (log.mismatchFlag) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }

            log.ocrExpiryDate?.let { expiryDate ->
                Text(
                    text = "Expiry: $expiryDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PocketCount(
    label: String,
    count: Int?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = count?.toString() ?: "—",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = "Loading report…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReportError(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = ReportCardShape
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun ReportEmptyState(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = ReportCardShape
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun isWithinWeek(
    timestamp: String?,
    start: LocalDate,
    endExclusive: LocalDate
): Boolean {
    val date = parseDateTime(timestamp)
        ?.atZone(ZoneId.systemDefault())
        ?.toLocalDate()
        ?: return false

    return !date.isBefore(start) && date.isBefore(endExclusive)
}

private fun parseDateTime(value: String?): Instant? {
    if (value.isNullOrBlank()) return null

    return try {
        OffsetDateTime.parse(value).toInstant()
    } catch (_: Exception) {
        try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    }
}

private fun formatTimestamp(value: String?): String {
    val instant = parseDateTime(value) ?: return "Unavailable"

    val formatter = DateTimeFormatter.ofPattern(
        "dd MMM yyyy, hh:mm a",
        Locale.getDefault()
    )

    return instant
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}