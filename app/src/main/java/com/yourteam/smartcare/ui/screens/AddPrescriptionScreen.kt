package com.yourteam.smartcare.ui.screens

import android.app.TimePickerDialog
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    // Backend format remains HH:mm:ss
    var timeSlot by remember { mutableStateOf("08:00:00") }
    var secondTimeSlot by remember { mutableStateOf("14:00:00") }
    var thirdTimeSlot by remember { mutableStateOf("20:00:00") }

    var totalStock by remember { mutableStateOf("") }

    // UI-only frequency selection
    var frequency by remember { mutableStateOf("Once daily") }

    // ---------------------------------------------------------
    // DISPLAY TIMES
    // ---------------------------------------------------------

    val displayTime = remember(timeSlot) {
        runCatching {
            LocalTime.parse(timeSlot)
                .format(
                    DateTimeFormatter.ofPattern(
                        "h:mm a",
                        Locale.getDefault()
                    )
                )
        }.getOrElse {
            "8:00 AM"
        }
    }

    val displaySecondTime = remember(secondTimeSlot) {
        runCatching {
            LocalTime.parse(secondTimeSlot)
                .format(
                    DateTimeFormatter.ofPattern(
                        "h:mm a",
                        Locale.getDefault()
                    )
                )
        }.getOrElse {
            "2:00 PM"
        }
    }

    val displayThirdTime = remember(thirdTimeSlot) {
        runCatching {
            LocalTime.parse(thirdTimeSlot)
                .format(
                    DateTimeFormatter.ofPattern(
                        "h:mm a",
                        Locale.getDefault()
                    )
                )
        }.getOrElse {
            "8:00 PM"
        }
    }

    // ---------------------------------------------------------
    // TIME PICKER
    // ---------------------------------------------------------

    fun showTimePicker(
        currentTime: String,
        onTimeSelected: (String) -> Unit
    ) {
        val parsedTime =
            runCatching {
                LocalTime.parse(currentTime)
            }.getOrElse {
                LocalTime.of(8, 0)
            }

        TimePickerDialog(
            context,
            { _, hour, minute ->

                val selectedTime =
                    LocalTime.of(hour, minute)

                // Keep backend/database format as HH:mm:ss
                val formattedTime =
                    selectedTime.format(
                        DateTimeFormatter.ofPattern(
                            "HH:mm:ss"
                        )
                    )

                onTimeSelected(formattedTime)
            },
            parsedTime.hour,
            parsedTime.minute,
            false
        ).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Add Prescription",
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
                            Icons.Default.ArrowBack,
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
                .padding(20.dp)
                .verticalScroll(
                    rememberScrollState()
                ),
            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {



            // ---------------------------------------------------------
            // PRESCRIPTION DETAILS
            // ---------------------------------------------------------

            Text(
                text = "Prescription Details",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color =
                    MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = medName,
                onValueChange = {
                    medName = it
                },
                label = {
                    Text("Medication Name")
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Vaccines,
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = dosage,
                onValueChange = {
                    dosage = it
                },
                label = {
                    Text(
                        "Dosage (e.g. 500mg, 1 Tablet)"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // ---------------------------------------------------------
            // FREQUENCY
            // ---------------------------------------------------------

            Text(
                text = "How often?",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                FilterChip(
                    selected =
                        frequency == "Once daily",
                    onClick = {
                        frequency = "Once daily"
                    },
                    label = {
                        Text(
                            "Once daily",
                            fontSize = 12.sp
                        )
                    },
                    modifier =
                        Modifier.weight(1f)
                )

                FilterChip(
                    selected =
                        frequency == "Twice daily",
                    onClick = {
                        frequency = "Twice daily"
                    },
                    label = {
                        Text(
                            "Twice daily",
                            fontSize = 12.sp
                        )
                    },
                    modifier =
                        Modifier.weight(1f)
                )

                FilterChip(
                    selected =
                        frequency == "Thrice daily",
                    onClick = {
                        frequency = "Thrice daily"
                    },
                    label = {
                        Text(
                            "Thrice daily",
                            fontSize = 12.sp
                        )
                    },
                    modifier =
                        Modifier.weight(1f)
                )
            }

            // ---------------------------------------------------------
            // FIRST TIME
            // ---------------------------------------------------------

            OutlinedTextField(
                value = displayTime,
                onValueChange = {},
                readOnly = true,
                label = {
                    Text(
                        when (frequency) {
                            "Twice daily",
                            "Thrice daily" ->
                                "First Schedule Time"

                            else ->
                                "Schedule Time"
                        }
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    TextButton(
                        onClick = {
                            showTimePicker(
                                timeSlot
                            ) { selectedTime ->
                                timeSlot = selectedTime
                            }
                        }
                    ) {
                        Text("Change")
                    }
                }
            )

            // ---------------------------------------------------------
            // SECOND TIME
            // Visible for Twice + Thrice daily
            // ---------------------------------------------------------

            if (
                frequency == "Twice daily" ||
                frequency == "Thrice daily"
            ) {

                OutlinedTextField(
                    value = displaySecondTime,
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text("Second Schedule Time")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                showTimePicker(
                                    secondTimeSlot
                                ) { selectedTime ->
                                    secondTimeSlot =
                                        selectedTime
                                }
                            }
                        ) {
                            Text("Change")
                        }
                    }
                )
            }

            // ---------------------------------------------------------
            // THIRD TIME
            // Visible only for Thrice daily
            // ---------------------------------------------------------

            if (frequency == "Thrice daily") {

                OutlinedTextField(
                    value = displayThirdTime,
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text("Third Schedule Time")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                showTimePicker(
                                    thirdTimeSlot
                                ) { selectedTime ->
                                    thirdTimeSlot =
                                        selectedTime
                                }
                            }
                        ) {
                            Text("Change")
                        }
                    }
                )

                Text(
                    text =
                        "Additional schedule times are currently "
                                + "for preview only. They will be connected "
                                + "to medication scheduling in the next step.",
                    fontSize = 12.sp,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            // ---------------------------------------------------------
            // STOCK
            // ---------------------------------------------------------

            OutlinedTextField(
                value = totalStock,
                onValueChange = {
                    totalStock = it
                },
                label = {
                    Text("Initial Stock / Pill Count")
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Inventory,
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            // ---------------------------------------------------------
            // SAVE
            // ---------------------------------------------------------

            Button(
                onClick = {

                    /*
                     * IMPORTANT:
                     *
                     * Step 2 is still UI-only.
                     *
                     * The existing Medication model supports only
                     * one scheduled_time value.
                     *
                     * Therefore only the FIRST selected time is
                     * currently sent to the existing backend.
                     *
                     * secondTimeSlot and thirdTimeSlot are NOT saved.
                     */

                    viewModel.addMedication(
                        medName = medName.trim(),
                        dosage = dosage.trim(),
                        scheduledTime =
                            timeSlot.trim(),
                        currentStock =
                            totalStock.toIntOrNull() ?: 0,
                        context = context
                    ) {
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled =
                    medName.isNotBlank() &&
                            dosage.isNotBlank() &&
                            totalStock.isNotBlank()
            ) {
                Text(
                    "Save Prescription",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}