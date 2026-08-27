package com.yourteam.smartcare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.data.repository.AuthRepository
import com.yourteam.smartcare.ui.navigation.Screen
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianSetupScreen(navController: NavController) {
    var elderPhone by remember { mutableStateOf("") }
    var primaryMedication by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var scheduledTime by remember { mutableStateOf("08:00:00") }
    var medicalHistory by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guardian Setup", fontWeight = FontWeight.Bold) },
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Register & Link Elder",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Enter the Elder's phone number to create their account and link them to your dashboard.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = elderPhone,
                onValueChange = { elderPhone = it },
                label = { Text("Elder's Phone Number") },
                placeholder = { Text("e.g. 9876543210") },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = primaryMedication,
                onValueChange = { primaryMedication = it },
                label = { Text("Primary Medication Name") },
                placeholder = { Text("e.g. Aspirin") },
                leadingIcon = { Icon(Icons.Default.MedicalServices, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text("Dosage") },
                placeholder = { Text("e.g. 500 mg or 1 Tablet") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = scheduledTime,
                onValueChange = { scheduledTime = it },
                label = { Text("Scheduled Time (HH:MM:SS)") },
                placeholder = { Text("08:00:00") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = medicalHistory,
                onValueChange = { medicalHistory = it },
                label = { Text("Medical Notes / History (Optional)") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            if (errorMessage != null) {
                Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val cleanPhone = elderPhone.trim()
                    if (cleanPhone.isBlank() || primaryMedication.isBlank() || dosage.isBlank()) {
                        errorMessage = "Please enter Elder's Phone Number, Medication, and Dosage."
                        return@Button
                    }
                    errorMessage = null
                    isLoading = true

                    scope.launch {
                        // Register the Elder account in Supabase (Repository handles the email and 6-digit PIN internally)
                        val linkResult = AuthRepository.registerAndLinkElder(
                            name = "Elder",
                            phone = cleanPhone
                        )

                        linkResult.onSuccess { linkedElder: UserInfo ->
                            try {
                                val newMed = Medication(
                                    elderId = linkedElder.id,
                                    medName = primaryMedication.trim(),
                                    dosage = dosage.trim(),
                                    scheduledTime = scheduledTime.trim(),
                                    currentStock = 30
                                )
                                SupabaseClient.client.from("medications").insert(newMed)

                                isLoading = false
                                navController.navigate(Screen.GuardianDashboard.route) {
                                    popUpTo(Screen.GuardianSetup.route) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Elder registered, but failed to save medication: ${e.message}"
                            }
                        }.onFailure { error: Throwable ->
                            isLoading = false
                            errorMessage = error.localizedMessage ?: "Failed to register Elder account."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Register Elder & Start Tracking", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}