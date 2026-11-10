package com.yourteam.smartcare.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yourteam.smartcare.data.model.AdherenceLog
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.ui.AdherenceReportUiState
import com.yourteam.smartcare.ui.MedicationUiState
import com.yourteam.smartcare.ui.MedicationViewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ReportStatus {
    TAKEN,
    MISSED,
    PENDING
}

private data class MedicationReportItem(
    val medication: Medication,
    val scheduledTime: String,
    val status: ReportStatus,
    val takenAt: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdherenceReportScreen(
    navController: NavController,
    viewModel: MedicationViewModel
) {
    val medicationState by viewModel.uiState.collectAsState()
    val adherenceState by viewModel.adherenceReportState.collectAsState()

    var selectedDate by remember {
        mutableStateOf(LocalDate.now())
    }

    val today = LocalDate.now()

    LaunchedEffect(Unit) {
        viewModel.fetchTodaySchedule()
        viewModel.loadAdherenceReport()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Adherence Report",
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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->

        when {
            medicationState is MedicationUiState.Loading ||
                    adherenceState is AdherenceReportUiState.Loading -> {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            medicationState is MedicationUiState.Error -> {

                val message =
                    (medicationState as MedicationUiState.Error).message

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error loading medications",
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(message)

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                viewModel.fetchTodaySchedule()
                                viewModel.loadAdherenceReport()
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            adherenceState is AdherenceReportUiState.Error -> {

                val message =
                    (adherenceState as AdherenceReportUiState.Error).message

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error loading adherence report",
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(message)

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                viewModel.loadAdherenceReport()
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            medicationState is MedicationUiState.Success &&
                    adherenceState is AdherenceReportUiState.Success -> {

                val medications =
                    (medicationState as MedicationUiState.Success).medications

                val logs =
                    (adherenceState as AdherenceReportUiState.Success).logs

                /*
                 * Build the report for the currently selected calendar date.
                 * The report is read-only and is derived from medications
                 * and their corresponding adherence logs.
                 */
                val reportItems = buildReportItems(
                    medications = medications,
                    logs = logs,
                    selectedDate = selectedDate
                )


                val takenCount =
                    reportItems.count { it.status == ReportStatus.TAKEN }

                val missedCount =
                    reportItems.count { it.status == ReportStatus.MISSED }

                val pendingCount =
                    reportItems.count { it.status == ReportStatus.PENDING }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    item {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Medication adherence",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Text(
                            text = "Track daily medication intake",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor =
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween
                                ) {

                                    IconButton(
                                        onClick = {
                                            selectedDate =
                                                selectedDate.minusDays(1)
                                        }
                                    ) {
                                        Icon(
                                            imageVector =
                                                Icons.Default.ArrowBack,
                                            contentDescription =
                                                "Previous day"
                                        )
                                    }

                                    Column(
                                        horizontalAlignment =
                                            Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = formatReportDate(
                                                selectedDate
                                            ),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )

                                        if (selectedDate == today) {
                                            Text(
                                                text = "Today",
                                                color =
                                                    MaterialTheme.colorScheme.primary,
                                                fontSize = 13.sp,
                                                fontWeight =
                                                    FontWeight.Medium
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            if (selectedDate.isBefore(today)) {
                                                selectedDate =
                                                    selectedDate.plusDays(1)
                                            }
                                        },
                                        enabled = selectedDate.isBefore(today)
                                    ) {
                                        Icon(
                                            imageVector =
                                                Icons.Default.ArrowForward,
                                            contentDescription =
                                                "Next day"
                                        )
                                    }
                                }

                                if (selectedDate != today) {
                                    Spacer(
                                        modifier = Modifier.height(4.dp)
                                    )

                                    Button(
                                        onClick = {
                                            selectedDate = today
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Today")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor =
                                    MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Daily Adherence",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )

                                Spacer(
                                    modifier = Modifier.height(8.dp)
                                )

                                Text(
                                    text = "$takenCount / ${reportItems.size}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 30.sp
                                )

                                Text(
                                    text = "Doses Taken",
                                    color =
                                        MaterialTheme.colorScheme
                                            .onPrimaryContainer
                                )

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(16.dp)
                                ) {
                                    SummaryItem(
                                        icon = {
                                            Icon(
                                                imageVector =
                                                    Icons.Default.Check,
                                                contentDescription = null,
                                                modifier =
                                                    Modifier.size(18.dp)
                                            )
                                        },
                                        text = "$takenCount Taken"
                                    )

                                    SummaryItem(
                                        icon = {
                                            Icon(
                                                imageVector =
                                                    Icons.Default.Close,
                                                contentDescription = null,
                                                modifier =
                                                    Modifier.size(18.dp)
                                            )
                                        },
                                        text = "$missedCount Missed"
                                    )

                                    SummaryItem(
                                        icon = {
                                            Icon(
                                                imageVector =
                                                    Icons.Default.Schedule,
                                                contentDescription = null,
                                                modifier =
                                                    Modifier.size(18.dp)
                                            )
                                        },
                                        text = "$pendingCount Pending"
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Medication Schedule",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    if (reportItems.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor =
                                        MaterialTheme.colorScheme
                                            .surfaceVariant
                                )
                            ) {
                                Text(
                                    text = "No prescriptions found.",
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    } else {
                        items(
                            items = reportItems,
                            key = { it.medication.id ?: it.medication.medName }
                        ) { item ->

                            MedicationReportCard(item)
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(
    icon: @Composable () -> Unit,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()

        Text(
            text = text,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun MedicationReportCard(
    item: MedicationReportItem
) {
    val statusText: String
    val statusIcon: @Composable () -> Unit

    when (item.status) {
        ReportStatus.TAKEN -> {
            statusText = "Taken"
            statusIcon = {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Taken",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        ReportStatus.MISSED -> {
            statusText = "Missed"
            statusIcon = {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Missed",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        ReportStatus.PENDING -> {
            statusText = "Pending"
            statusIcon = {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Pending",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.medication.medName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Text(
                    text = "${item.medication.dosage} • ${item.scheduledTime}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                /*
                 * Show the actual time the medication was taken.
                 * The timestamp from Supabase is converted from UTC
                 * to the device's local timezone for display.
                 */
                if (item.status == ReportStatus.TAKEN &&
                    item.takenAt != null
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Taken at ${item.takenAt}",
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.size(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                statusIcon()

                Spacer(modifier = Modifier.size(4.dp))

                Text(
                    text = statusText,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/*
 * Builds the medication list for the selected calendar date.
 *
 * Rules:
 * 1. Match adherence logs using the selected date in the device timezone.
 * 2. Exclude medications that were created after their scheduled occurrence.
 * 3. A "taken" log produces TAKEN.
 * 4. A "missed" log produces MISSED.
 * 5. No normal adherence log produces PENDING.
 * 6. Non-standard statuses such as "overdose_flag" are ignored
 *    when determining normal adherence.
 */
private fun buildReportItems(
    medications: List<Medication>,
    logs: List<AdherenceLog>,
    selectedDate: LocalDate
): List<MedicationReportItem> {

    val zone = ZoneId.systemDefault()

    /*
     * Supabase timestamps contain timezone information.
     * Convert them to the device timezone before comparing
     * their calendar date with the selected report date.
     */
    val logsForDate = logs.filter { log ->
        try {
            OffsetDateTime.parse(log.scheduledAt)
                .toInstant()
                .atZone(zone)
                .toLocalDate() == selectedDate
        } catch (e: Exception) {
            Log.e(
                "P7_REPORT",
                "Failed to parse scheduledAt=${log.scheduledAt}",
                e
            )
            false
        }
    }



    return medications
        .filter { medication ->

            /*
             * A medication should appear in a historical report only
             * if it already existed when its scheduled dose occurred.
             *
             * This prevents medications created on later dates from
             * incorrectly appearing as Pending on earlier dates.
             */
            try {
                val scheduledLocalTime =
                    LocalTime.parse(medication.scheduledTime)

                val scheduledOccurrence =
                    selectedDate
                        .atTime(scheduledLocalTime)
                        .atZone(zone)
                        .toInstant()

                val createdAt = medication.createdAt

                if (createdAt == null) {
                    true
                } else {
                    val createdInstant =
                        OffsetDateTime.parse(createdAt).toInstant()

                    !createdInstant.isAfter(scheduledOccurrence)
                }

            } catch (e: Exception) {
                /*
                 * If a historical-date check cannot be evaluated,
                 * keep the medication rather than silently removing it.
                 */
                Log.e(
                    "P7_REPORT",
                    "Failed historical-date check for " +
                            "medication=${medication.medName}, " +
                            "createdAt=${medication.createdAt}, " +
                            "scheduledTime=${medication.scheduledTime}",
                    e
                )

                true
            }
        }
        .map { medication ->

            /*
             * Find adherence logs belonging to this medication
             * on the selected calendar date.
             */
            val medicationLogs =
                logsForDate.filter {
                    it.medicationId == medication.id
                }

            /*
             * Ignore non-standard statuses such as "overdose_flag".
             * Only "taken" and "missed" determine normal adherence.
             *
             * If there is no normal adherence log, the dose remains Pending.
             * Absence of a log must not automatically be treated as Missed.
             */
            val log = medicationLogs
                .lastOrNull {
                    it.status == "taken" ||
                            it.status == "missed"
                }

            val status: ReportStatus
            val takenAt: String?

            if (log == null) {

                status = ReportStatus.PENDING
                takenAt = null

            } else if (log.status == "taken") {

                status = ReportStatus.TAKEN

                /*
                 * Convert the stored UTC timestamp to the device's
                 * local timezone before displaying it.
                 */
                takenAt = log.takenAt?.let {
                    formatLocalDateTime(it)
                }

            } else {

                status = ReportStatus.MISSED
                takenAt = null
            }

            MedicationReportItem(
                medication = medication,
                scheduledTime =
                    formatScheduledTime(
                        medication.scheduledTime
                    ),
                status = status,
                takenAt = takenAt
            )
        }
        .sortedBy {
            parseScheduledTime(it.medication.scheduledTime)
        }
}

private fun formatReportDate(
    date: LocalDate
): String {
    return date.format(
        DateTimeFormatter.ofPattern(
            "MMMM d, yyyy",
            Locale.getDefault()
        )
    )
}

/*
 * Converts a Supabase timestamp with an explicit UTC offset
 * into a user-friendly local time.
 */
private fun formatLocalDateTime(
    timestamp: String
): String? {
    return runCatching {
        OffsetDateTime.parse(timestamp)
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .format(
                DateTimeFormatter.ofPattern(
                    "h:mm a",
                    Locale.getDefault()
                )
            )
    }.getOrNull()
}

private fun formatScheduledTime(
    time: String
): String {
    return runCatching {
        LocalTime.parse(time)
            .format(
                DateTimeFormatter.ofPattern(
                    "h:mm a",
                    Locale.getDefault()
                )
            )
    }.getOrElse {
        time
    }
}

private fun parseScheduledTime(
    time: String
): LocalTime {
    return runCatching {
        LocalTime.parse(time)
    }.getOrElse {
        LocalTime.MAX
    }
}