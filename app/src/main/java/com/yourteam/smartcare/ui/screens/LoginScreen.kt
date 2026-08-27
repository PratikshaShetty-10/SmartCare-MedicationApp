package com.yourteam.smartcare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yourteam.smartcare.data.repository.AuthRepository
import com.yourteam.smartcare.ui.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    var selectedRole by remember { mutableStateOf("Elder") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var pinCode by remember { mutableStateOf("") } // 6-digit PIN

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to SmartCare",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedRole == "Elder",
                    onClick = { selectedRole = "Elder"; errorMessage = null },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Elder Login")
                }
                SegmentedButton(
                    selected = selectedRole == "Guardian",
                    onClick = { selectedRole = "Guardian"; errorMessage = null },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Guardian Login")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedRole == "Elder") {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("Enter registered phone number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { pinCode = it },
                    label = { Text("6-Digit PIN Code") },
                    placeholder = { Text("Enter 6-digit PIN given by Guardian") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true

                    scope.launch {
                        if (selectedRole == "Elder") {
                            val cleanPhone = phoneNumber.trim()
                            val cleanPin = pinCode.trim()

                            if (cleanPhone.isBlank() || cleanPin.isBlank()) {
                                errorMessage = "Please enter phone number and 6-digit PIN."
                                isLoading = false
                                return@launch
                            }

                            // Pass BOTH phone and cleanPin to AuthRepository.loginElder
                            val result = AuthRepository.loginElder(cleanPhone, cleanPin)
                            result.onSuccess { _: Unit ->
                                isLoading = false
                                navController.navigate(Screen.PatientHome.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            }.onFailure { _: Throwable ->
                                isLoading = false
                                errorMessage = "Invalid Phone Number or PIN code."
                            }
                        } else {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Please enter email and password."
                                isLoading = false
                                return@launch
                            }

                            val result = AuthRepository.loginGuardian(email.trim(), password.trim())
                            result.onSuccess { _: Unit ->
                                isLoading = false
                                navController.navigate(Screen.GuardianDashboard.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            }.onFailure { error: Throwable ->
                                isLoading = false
                                errorMessage = error.localizedMessage ?: "Login failed."
                            }
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
                    Text(if (selectedRole == "Elder") "Log In" else "Guardian Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = { navController.navigate(Screen.Register.route) }
            ) {
                Text("Need a Guardian Account? Sign Up")
            }
        }
    }
}