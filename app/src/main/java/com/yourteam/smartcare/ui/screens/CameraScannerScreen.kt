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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.yourteam.smartcare.BuildConfig
import com.yourteam.smartcare.YoloOnnxDetector
import com.yourteam.smartcare.data.SupabaseClient
import io.github.jan.supabase.auth.auth
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
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasCameraPermission = granted
        }

    // General UI state
    var scannedResult by remember {
        mutableStateOf("Position the medicine inside the guide")
    }
    var isScanning by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Original YOLO predictions. Do not modify these when the elder edits counts.
    var filledTabletCount by remember { mutableStateOf(0) }
    var brokenTabletCount by remember { mutableStateOf(0) }
    var emptyPocketCount by remember { mutableStateOf(0) }

    // Scan result state
    var annotatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showConfirmationUI by remember { mutableStateOf(false) }
    var showSaveConfirmationDialog by remember { mutableStateOf(false) }

    // Elder-confirmed editable counts
    var editableCount by remember { mutableStateOf("") }
    var editableEmptyCount by remember { mutableStateOf("") }
    var editableBrokenCount by remember { mutableStateOf("") }
    var editableExpectedCount by remember { mutableStateOf("") }

    // Medication state
    var medicationList by remember {
        mutableStateOf<List<Medication>>(emptyList())
    }
    var selectedMedication by remember {
        mutableStateOf<Medication?>(null)
    }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        coroutineScope.launch(Dispatchers.IO) {
            val fetchedList = fetchMedicationsFromSupabase()

            withContext(Dispatchers.Main) {
                medicationList = fetchedList


                if (fetchedList.isNotEmpty()) {
                    selectedMedication = fetchedList[0]
                    editableExpectedCount = ""
                }

            }
        }
    }

    // Save original AI predictions and elder-confirmed counts separately.
    val saveScan: () -> Unit = {
        val finalCount = editableCount.toIntOrNull()
        val finalEmpty = editableEmptyCount.toIntOrNull()
        val finalBroken = editableBrokenCount.toIntOrNull()
        val finalExpected = editableExpectedCount.toIntOrNull()
        val activeMed = selectedMedication

        when {
            activeMed == null -> {
                scannedResult = "Please select a medication."
                showSaveConfirmationDialog = false
            }

            finalCount == null || finalCount < 0 ||
                    finalEmpty == null || finalEmpty < 0 ||
                    finalBroken == null || finalBroken < 0 ||
                    finalExpected == null || finalExpected < 0 -> {
                scannedResult =
                    "Enter valid non-negative counts before saving."
                showSaveConfirmationDialog = false
            }

            isSaving -> {
                // Prevent duplicate submissions.
            }

            else -> {
                isSaving = true
                scannedResult = "Saving scan..."

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        // Use the existing authenticated Supabase session.
                        val accessToken =
                            SupabaseClient.client.auth
                                .currentAccessTokenOrNull()
                                ?: throw IllegalStateException(
                                    "No active Supabase session. Please log in again."
                                )

                        val supabaseUrl = BuildConfig.SUPABASE_URL
                        val anonKey = BuildConfig.SUPABASE_KEY
                        val url =
                            "$supabaseUrl/rest/v1/verification_logs"

                        val jsonBody = JSONObject().apply {
                            put("medication_id", activeMed.id)

                            // Preserve the existing columns.

                            put("expected_count", finalExpected)

// Total pockets detected by the original YOLO predictions.
                            val yoloTotal =
                                filledTabletCount + emptyPocketCount + brokenTabletCount

                            put("actual_count_yolo", yoloTotal)
                            put("mismatch_flag", yoloTotal != finalExpected)


                            // Original YOLO predictions.
                            put("ai_filled_count", filledTabletCount)
                            put("ai_empty_count", emptyPocketCount)
                            put("ai_broken_count", brokenTabletCount)

                            // Counts confirmed or corrected by the elder.
                            put("confirmed_filled_count", finalCount)
                            put("confirmed_empty_count", finalEmpty)
                            put("confirmed_broken_count", finalBroken)

                            // Intentionally omit:
                            // ocr_expiry_date
                            //
                            // verified_at is generated by the database.
                        }.toString()

                        val requestBody = jsonBody.toRequestBody(
                            "application/json".toMediaTypeOrNull()
                        )

                        val request = Request.Builder()
                            .url(url)
                            .addHeader("apikey", anonKey)
                            .addHeader(
                                "Authorization",
                                "Bearer $accessToken"
                            )
                            .addHeader(
                                "Content-Type",
                                "application/json"
                            )
                            .addHeader("Prefer", "return=minimal")
                            .post(requestBody)
                            .build()

                        OkHttpClient().newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                val errorBody =
                                    response.body?.string().orEmpty()

                                throw Exception(
                                    "Supabase returned HTTP ${response.code}: " +
                                            errorBody
                                )
                            }
                        }

                        withContext(Dispatchers.Main) {
                            scannedResult = "Scan saved successfully"
                            showConfirmationUI = false
                            showSaveConfirmationDialog = false
                            annotatedBitmap = null
                            isSaving = false
                        }
                    } catch (e: Exception) {
                        Log.e(
                            "SmartCareDB",
                            "Supabase verification save failed",
                            e
                        )

                        withContext(Dispatchers.Main) {
                            scannedResult =
                                "Database save failed. Check Logcat."
                            showSaveConfirmationDialog = false
                            isSaving = false
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (showConfirmationUI) {
                            "Scan Results"
                        } else {
                            "SmartCare Scanner"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (showConfirmationUI) {
                                showConfirmationUI = false
                                showSaveConfirmationDialog = false
                            } else {
                                navController.popBackStack()
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor =
                        MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->

        // SCREEN 1: CAMERA
        if (!showConfirmationUI) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black)
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = { ctx ->
                            val view = PreviewView(ctx)
                            previewView = view

                            val cameraProviderFuture =
                                ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener(
                                {
                                    val cameraProvider =
                                        cameraProviderFuture.get()

                                    val preview = Preview.Builder()
                                        .build()
                                        .also {
                                            it.setSurfaceProvider(
                                                view.surfaceProvider
                                            )
                                        }

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview
                                        )
                                    } catch (e: Exception) {
                                        Log.e(
                                            "SmartCareScanner",
                                            "Could not start camera preview",
                                            e
                                        )
                                    }
                                },
                                ContextCompat.getMainExecutor(ctx)
                            )

                            view
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Blister-pack alignment guide
                    Box(
                        modifier = Modifier
                            .size(width = 280.dp, height = 180.dp)
                            .align(Alignment.Center)
                            .border(
                                2.dp,
                                Color.Cyan,
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Text(
                            text = "Align Blister Pack Here",
                            color = Color.Cyan,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                        )
                    }
                } else {
                    Text(
                        "Camera permission required.",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Camera controls
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(20.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = scannedResult,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Button(
                            onClick = {
                                val bitmap = previewView?.bitmap

                                if (bitmap == null) {
                                    scannedResult =
                                        "Camera image not ready. Try again."
                                    return@Button
                                }

                                if (isScanning || isSaving) {
                                    return@Button
                                }

                                isScanning = true
                                scannedResult = "Analyzing medicine..."

                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val counts =
                                            YoloOnnxDetector.detect(
                                                context.applicationContext,
                                                bitmap
                                            )

                                        val resultBitmap =
                                            YoloOnnxDetector.drawDetections(
                                                bitmap,
                                                counts.detections
                                            )

                                        withContext(Dispatchers.Main) {
                                            // Preserve original AI results.
                                            filledTabletCount =
                                                counts.filledTablets
                                            brokenTabletCount =
                                                counts.brokenTablets
                                            emptyPocketCount =
                                                counts.emptyPockets

                                            // Initialize editable values
                                            // from the original predictions.
                                            editableCount =
                                                counts.filledTablets.toString()
                                            editableEmptyCount =
                                                counts.emptyPockets.toString()
                                            editableBrokenCount =
                                                counts.brokenTablets.toString()

                                            annotatedBitmap = resultBitmap
                                            scannedResult =
                                                "Review your scan results"
                                            isScanning = false
                                            showConfirmationUI = true
                                        }
                                    } catch (e: Exception) {
                                        Log.e(
                                            "SmartCareScanner",
                                            "Local YOLO inference failed",
                                            e
                                        )

                                        withContext(Dispatchers.Main) {
                                            isScanning = false
                                            scannedResult =
                                                "Scan failed: " +
                                                        (e.message
                                                            ?: "Unknown error")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            enabled = hasCameraPermission &&
                                    !isScanning &&
                                    !isSaving
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White
                                )
                                Spacer(Modifier.size(12.dp))
                                Text("Analyzing...")
                            } else {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = null
                                )
                                Spacer(Modifier.size(8.dp))
                                Text("Scan Medicine")
                            }
                        }
                    }
                }
            }
        } else {
            // SCREEN 2: RESULTS AND CONFIRMATION
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Review Your Medicine",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Check the detection image, correct any inaccurate " +
                                "counts, and select the corresponding medication.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Annotated image
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 3.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "YOLO Detection Image",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        annotatedBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription =
                                    "Annotated medicine image showing " +
                                            "filled tablets, broken tablets, " +
                                            "and empty pockets",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        min = 280.dp,
                                        max = 440.dp
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentScale = ContentScale.Fit
                            )
                        } ?: Text(
                            "No scan image is available. Please scan again.",
                            color = MaterialTheme.colorScheme.error
                        )

                        Text(
                            "The counts below show the original AI predictions. " +
                                    "You can correct them in the form.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Original AI predictions
                Text(
                    "Original AI Detection Summary",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetectionCountCard(
                        label = "Filled",
                        count = filledTabletCount,
                        modifier = Modifier.weight(1f)
                    )

                    DetectionCountCard(
                        label = "Broken",
                        count = brokenTabletCount,
                        modifier = Modifier.weight(1f)
                    )

                    DetectionCountCard(
                        label = "Empty",
                        count = emptyPocketCount,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Editable human verification form
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Confirm Scan Counts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Correct any AI mistakes after checking the " +
                                    "blister pack. Your confirmed counts will be " +
                                    "saved separately from the original predictions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = editableCount,
                            onValueChange = { input ->
                                if (input.isEmpty() ||
                                    input.all { it.isDigit() }
                                ) {
                                    editableCount = input
                                }
                            },
                            label = {
                                Text("Confirm Filled Tablet Count")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editableEmptyCount,
                            onValueChange = { input ->
                                if (input.isEmpty() ||
                                    input.all { it.isDigit() }
                                ) {
                                    editableEmptyCount = input
                                }
                            },
                            label = {
                                Text("Confirm Empty Pocket Count")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editableBrokenCount,
                            onValueChange = { input ->
                                if (input.isEmpty() ||
                                    input.all { it.isDigit() }
                                ) {
                                    editableBrokenCount = input
                                }
                            },
                            label = {
                                Text("Confirm Broken Tablet Count")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Medication selection
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedMedication?.name
                                    ?: if (medicationList.isEmpty()) {
                                        "No medications loaded"
                                    } else {
                                        "Select medication"
                                    },
                                onValueChange = {},
                                label = {
                                    Text("Select Medication")
                                },
                                readOnly = true,
                                singleLine = true,
                                enabled = !isSaving,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    TextButton(
                                        onClick = {
                                            isDropdownExpanded =
                                                !isDropdownExpanded
                                        },
                                        enabled = !isSaving
                                    ) {
                                        Text("▼")
                                    }
                                }
                            )

                            DropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = {
                                    isDropdownExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                medicationList.forEach { med ->
                                    DropdownMenuItem(
                                        text = { Text(med.name) },
                                        onClick = {

                                            selectedMedication = med
                                            editableExpectedCount = ""
                                            isDropdownExpanded = false

                                        },
                                        enabled = !isSaving
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = editableExpectedCount,
                            onValueChange = { input ->
                                if (input.isEmpty() ||
                                    input.all { it.isDigit() }
                                ) {
                                    editableExpectedCount = input
                                }
                            },
                            label = {
                                Text("Expected Total Pockets in This Strip")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            singleLine = true,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "Scan time is recorded automatically when the " +
                                    "scan is saved. Expiry-date detection has not " +
                                    "been integrated yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                val filled =
                                    editableCount.toIntOrNull()
                                val empty =
                                    editableEmptyCount.toIntOrNull()
                                val broken =
                                    editableBrokenCount.toIntOrNull()
                                val expected =
                                    editableExpectedCount.toIntOrNull()

                                when {
                                    selectedMedication == null -> {
                                        scannedResult =
                                            "Please select a medication."
                                    }

                                    filled == null || filled < 0 ||
                                            empty == null || empty < 0 ||
                                            broken == null || broken < 0 ||
                                            expected == null || expected < 0 -> {
                                        scannedResult =
                                            "Enter valid non-negative counts."
                                    }

                                    else -> {
                                        scannedResult =
                                            "Please review your confirmed counts."
                                        showSaveConfirmationDialog = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            enabled = !isSaving
                        ) {
                            Text("Review & Save Scan")
                        }

                        OutlinedButton(
                            onClick = {
                                showConfirmationUI = false
                                showSaveConfirmationDialog = false
                                annotatedBitmap = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving
                        ) {
                            Text("Scan Again")
                        }
                    }
                }

                Spacer(Modifier.size(8.dp))
            }
        }
    }

    // Explicit confirmation before saving to Supabase.
    if (showSaveConfirmationDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    showSaveConfirmationDialog = false
                }
            },
            title = {
                Text("Confirm Scan Counts")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Medication: " +
                                (selectedMedication?.name ?: "Not selected")
                    )

                    Text("Original AI predictions:")
                    Text("Filled: $filledTabletCount")
                    Text("Empty: $emptyPocketCount")
                    Text("Broken: $brokenTabletCount")

                    Text("Your confirmed counts:")
                    Text("Filled: ${editableCount.ifBlank { "Not entered" }}")
                    Text(
                        "Empty: ${
                            editableEmptyCount.ifBlank { "Not entered" }
                        }"
                    )
                    Text(
                        "Broken: ${
                            editableBrokenCount.ifBlank { "Not entered" }
                        }"
                    )

                    Text(
                        "Expected count: " +
                                editableExpectedCount.ifBlank { "Not entered" }
                    )

                    Text(
                        "Check the counts before saving. The expiry date " +
                                "will remain unset until OCR is implemented."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = saveScan,
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Saving...")
                    } else {
                        Text("Confirm & Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isSaving) {
                            showSaveConfirmationDialog = false
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text("Review Again")
                }
            }
        )
    }
}

@Composable
private fun DetectionCountCard(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// Medication used by this scanner screen.
data class Medication(
    val id: String,
    val name: String,
    val expectedCount: Int
)

// Fetch only the logged-in user's medications from Supabase.
private fun fetchMedicationsFromSupabase(): List<Medication> {
    return try {
        val auth = SupabaseClient.client.auth

        // Identify the currently authenticated user.
        val elderId = auth.currentUserOrNull()?.id
            ?: throw IllegalStateException(
                "No logged-in user found. Please log in again."
            )

        // Use the active user's access token, not the anon key,
        // for the Authorization header.
        val accessToken = auth.currentAccessTokenOrNull()
            ?: throw IllegalStateException(
                "No active Supabase session. Please log in again."
            )

        val supabaseUrl = BuildConfig.SUPABASE_URL
        val anonKey = BuildConfig.SUPABASE_KEY

        // Filter the results to the authenticated user's elder_id.
        val url = "$supabaseUrl/rest/v1/medications" +
                "?select=*&elder_id=eq.$elderId"

        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        OkHttpClient().newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: "[]"

            Log.d(
                "SmartCareDB",
                "Medication fetch status: ${response.code}"
            )

            if (response.isSuccessful) {
                val jsonArray = JSONArray(responseBody)
                val list = mutableListOf<Medication>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)

                    list.add(
                        Medication(
                            id = item.optString("id", ""),
                            name = item.optString("med_name", "Unknown"),
                            expectedCount = item.optInt("current_stock", 0)
                        )
                    )
                }

                list
            } else {
                Log.e(
                    "SmartCareDB",
                    "Medication fetch failed: $responseBody"
                )
                emptyList()
            }
        }
    } catch (e: Exception) {
        Log.e(
            "SmartCareDB",
            "Exception while fetching medications",
            e
        )
        emptyList()
    }
}

// Legacy network-analysis function retained for compatibility.
private fun sendElderScanRequest(bitmap: Bitmap): AIResponse {
    val renderBaseUrl =
        "https://orange-cod-r4vx9g9r5gwwhpjg9-5000.app.github.dev"
    val endpoint = "$renderBaseUrl/analyze"

    return try {
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width / 2).coerceAtLeast(1),
            (bitmap.height / 2).coerceAtLeast(1),
            true
        )

        val stream = ByteArrayOutputStream()
        scaledBitmap.compress(
            Bitmap.CompressFormat.JPEG,
            80,
            stream
        )
        val byteArray = stream.toByteArray()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                "pill.jpg",
                byteArray.toRequestBody(
                    "image/jpeg".toMediaTypeOrNull()
                )
            )
            .build()

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "{}"
                val json = JSONObject(responseBody)

                AIResponse(
                    okPills = json.optInt("ok_pills", 0),
                    ocrText = json.optString("ocr_text", "")
                )
            } else {
                AIResponse(
                    error = "Error: Server returned ${response.code}"
                )
            }
        }
    } catch (e: Exception) {
        Log.e(
            "SmartCareScanner",
            "Network analysis failed",
            e
        )
        AIResponse(
            error = "Network error. Check Logcat for details."
        )
    }
}

data class AIResponse(
    val okPills: Int = 0,
    val ocrText: String = "",
    val error: String? = null
)