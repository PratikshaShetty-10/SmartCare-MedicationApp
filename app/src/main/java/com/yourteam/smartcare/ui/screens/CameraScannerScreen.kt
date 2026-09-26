package com.yourteam.smartcare.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// IMPORT YOUR BUILD CONFIG HERE
import com.yourteam.smartcare.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    // --- UI STATE VARIABLES ---
    var scannedResult by remember { mutableStateOf("Hold steady to scan tablet") }
    var isScanning by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // YOLO Edit States
    var editableCount by remember { mutableStateOf("") }
    var editableOcrText by remember { mutableStateOf("") }
    var editableScanDate by remember { mutableStateOf("") }
    var showConfirmationUI by remember { mutableStateOf(false) }

    // Supabase Dropdown States
    var medicationList by remember { mutableStateOf<List<Medication>>(emptyList()) }
    var selectedMedication by remember { mutableStateOf<Medication?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var editableExpectedCount by remember { mutableStateOf("") }

    // Fetch Medications on Screen Load
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)

        coroutineScope.launch(Dispatchers.IO) {
            val fetchedList = fetchMedicationsFromSupabase()
            withContext(Dispatchers.Main) {
                medicationList = fetchedList
                if (fetchedList.isNotEmpty()) {
                    selectedMedication = fetchedList[0]
                    editableExpectedCount = fetchedList[0].expectedCount.toString()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartCare Scanner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {

            // Camera View
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val view = PreviewView(ctx)
                        previewView = view
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(view.surfaceProvider)
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                            } catch (_: Exception) { }
                        }, ContextCompat.getMainExecutor(ctx))
                        view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("Camera permission required.", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }

            // Target Overlay Box
            Box(
                modifier = Modifier
                    .size(width = 280.dp, height = 180.dp)
                    .align(Alignment.Center)
                    .border(3.dp, Color.Cyan, RoundedCornerShape(16.dp))
            ) {
                Text(
                    text = "Align Blister Pack Here",
                    color = Color.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                )
            }

            // Bottom UI Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = scannedResult,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // --- HUMAN VERIFICATION UI ---
                    if (showConfirmationUI) {

                        OutlinedTextField(
                            value = editableCount,
                            onValueChange = { editableCount = it },
                            label = { Text("Confirm Pill Count (AI Found)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Supabase Dropdown Menu
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedMedication?.name ?: "Loading...",
                                onValueChange = { },
                                label = { Text("Select Medicine (AI Read: $editableOcrText)") },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { isDropdownExpanded = !isDropdownExpanded }) {
                                        Text("▼")
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                medicationList.forEach { med ->
                                    DropdownMenuItem(
                                        text = { Text(med.name) },
                                        onClick = {
                                            selectedMedication = med
                                            editableExpectedCount = med.expectedCount.toString()
                                            isDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = editableExpectedCount,
                            onValueChange = { editableExpectedCount = it },
                            label = { Text("Expected Pills in THIS Strip") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editableScanDate,
                            onValueChange = { editableScanDate = it },
                            label = { Text("Date of Scan (YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                val finalCount = editableCount.toIntOrNull() ?: 0
                                val finalExpected = editableExpectedCount.toIntOrNull() ?: 0
                                val finalDate = editableScanDate
                                val activeMed = selectedMedication

                                if (activeMed == null) {
                                    scannedResult = "❌ Wait for medications to load"
                                    return@Button
                                }

                                scannedResult = "Saving to Database..."

                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val supabaseUrl = BuildConfig.SUPABASE_URL
                                        val anonKey = BuildConfig.SUPABASE_KEY
                                        val url = "$supabaseUrl/rest/v1/verification_logs"

                                        val jsonBody = JSONObject().apply {
                                            put("medication_id", activeMed.id)
                                            put("expected_count", finalExpected)
                                            put("actual_count_yolo", finalCount)
                                            put("ocr_expiry_date", finalDate)
                                            put("mismatch_flag", finalCount != finalExpected)
                                        }.toString()

                                        val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())
                                        val request = Request.Builder()
                                            .url(url)
                                            .addHeader("apikey", anonKey)
                                            .addHeader("Authorization", "Bearer $anonKey")
                                            .addHeader("Content-Type", "application/json")
                                            .addHeader("Prefer", "return=minimal")
                                            .post(requestBody)
                                            .build()

                                        val response = OkHttpClient().newCall(request).execute()

                                        if (response.isSuccessful) {
                                            withContext(Dispatchers.Main) {
                                                scannedResult = "✅ Saved & Guardian Alerted!"
                                                showConfirmationUI = false
                                            }
                                        } else {
                                            throw Exception("Code: ${response.code}")
                                        }

                                    } catch (e: Exception) {
                                        Log.e("SmartCareDB", "Supabase Error", e)
                                        withContext(Dispatchers.Main) { scannedResult = "❌ Database Error." }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save & Alert Guardian")
                        }
                    }

                    // --- CAMERA CAPTURE BUTTON ---
                    if (!showConfirmationUI) {
                        IconButton(
                            onClick = {
                                val bitmap = previewView?.bitmap
                                if (bitmap != null && !isScanning) {
                                    isScanning = true
                                    scannedResult = "Analyzing with YOLO Server..."
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val aiResponse = sendElderScanRequest(bitmap)
                                        withContext(Dispatchers.Main) {
                                            isScanning = false

                                            if (aiResponse.error != null) {
                                                scannedResult = aiResponse.error
                                            } else {
                                                scannedResult = "Review the detected data:"
                                                editableCount = aiResponse.okPills.toString()
                                                editableOcrText = aiResponse.ocrText

                                                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                                editableScanDate = currentDate

                                                showConfirmationUI = true
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            enabled = !isScanning && hasCameraPermission
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Capture",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- DATA CLASSES ---
data class AIResponse(
    val okPills: Int = 0,
    val ocrText: String = "",
    val error: String? = null
)

data class Medication(
    val id: String,
    val name: String,
    val expectedCount: Int
)

// --- SUPABASE FETCH NETWORK LOGIC ---
private fun fetchMedicationsFromSupabase(): List<Medication> {
    return try {
        val supabaseUrl = BuildConfig.SUPABASE_URL
        val anonKey = BuildConfig.SUPABASE_KEY

        // 🚨 CHANGED: select=* grabs ALL columns so it doesn't crash on name mismatch
        val url = "$supabaseUrl/rest/v1/medications?select=*"

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .get()
            .build()

        val response = OkHttpClient().newCall(request).execute()
        val responseBody = response.body?.string() ?: "[]"

        // 🚨 CRITICAL LOGGING - Check Logcat for "SmartCareDB" to see the real error!
        Log.e("SmartCareDB", "STATUS CODE: ${response.code}")
        Log.e("SmartCareDB", "RESPONSE DATA: $responseBody")

        if (response.isSuccessful) {
            val jsonArray = JSONArray(responseBody)
            val list = mutableListOf<Medication>()

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                list.add(
                    Medication(
                        id = item.optString("id", ""),
                        // Checks for 'name', and if missing, checks for 'medicine_name'
                        name = item.optString("name", item.optString("medicine_name", "Unknown")),
                        expectedCount = item.optInt("expected_count", 0)
                    )
                )
            }
            list
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        Log.e("SmartCareDB", "Exception while fetching", e)
        emptyList()
    }
}

// --- YOLO CODESPACES NETWORK LOGIC ---
private fun sendElderScanRequest(bitmap: Bitmap): AIResponse {
    val renderBaseUrl = "https://orange-cod-r4vx9g9r5gwwhpjg9-5000.app.github.dev"
    val endpoint = "$renderBaseUrl/analyze"

    return try {
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            bitmap.width / 2,
            bitmap.height / 2,
            true
        )

        val stream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "pill.jpg", byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder().url(endpoint).post(requestBody).build()
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val response = client.newCall(request).execute()

        if (response.isSuccessful) {
            val responseBody = response.body?.string() ?: "{}"
            val json = JSONObject(responseBody)

            val pills = json.optInt("ok_pills", 0)
            val text = json.optString("ocr_text", "")

            AIResponse(okPills = pills, ocrText = text)
        } else {
            AIResponse(error = "Error: Server returned ${response.code}")
        }
    } catch (e: Exception) {
        Log.e("SmartCareScanner", "CRITICAL NETWORK ERROR", e)
        AIResponse(error = "Network Error. Check Logcat for details.")
    }
}