
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yourteam.smartcare.ExpiryDateRegionDetector
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.data.repository.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val EXPIRY_CAMERA_TAG = "ExpiryCamera"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryDateScannerScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val repository = remember { MedicationRepository() }
    val detector = remember { ExpiryDateRegionDetector() }

    // Keep the same PreviewView instance while this screen is active.
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var cameraReady by remember { mutableStateOf(false) }
    var medications by remember { mutableStateOf<List<Medication>>(emptyList()) }
    var selectedMedication by remember { mutableStateOf<Medication?>(null) }
    var medicationMenuExpanded by remember { mutableStateOf(false) }

    var detectedCrop by remember { mutableStateOf<Bitmap?>(null) }
    var rawOcrText by remember { mutableStateOf("") }
    var confirmedDate by remember { mutableStateOf("") }
    var message by remember {
        mutableStateOf("Select a medication to begin scanning.")
    }
    var isBusy by remember { mutableStateOf(false) }
    var saveSucceeded by remember { mutableStateOf(false) }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            cameraGranted = granted
            message = if (granted) {
                "Camera permission granted. Starting camera…"
            } else {
                "Camera permission is required to scan an expiry label."
            }
        }

    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        try {
            medications = withContext(Dispatchers.IO) {
                repository.getTodaySchedule()
            }

            if (medications.isEmpty()) {
                message = "No medications were found for this account."
            }
        } catch (e: Exception) {
            Log.e(EXPIRY_CAMERA_TAG, "Could not load medications", e)
            message = "Could not load medications: ${e.localizedMessage ?: "Please try again."}"
        }
    }

    // Camera lifecycle binding.
    // Do not call unbindAll(), because other camera workflows must be preserved.
    DisposableEffect(cameraGranted, previewView, lifecycleOwner) {
        if (!cameraGranted) {
            cameraReady = false
            onDispose { }
        } else {
            var disposed = false
            var boundProvider: ProcessCameraProvider? = null
            var boundPreview: Preview? = null

            cameraReady = false

            val providerFuture = ProcessCameraProvider.getInstance(context)

            providerFuture.addListener(
                {
                    if (disposed) return@addListener

                    try {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build()

                        preview.setSurfaceProvider(previewView.surfaceProvider)

                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview
                        )

                        boundProvider = provider
                        boundPreview = preview
                        cameraReady = true

                        Log.d(EXPIRY_CAMERA_TAG, "Camera preview bound successfully")
                        message = "Camera ready. Position the expiry label in view."
                    } catch (e: Exception) {
                        Log.e(EXPIRY_CAMERA_TAG, "Camera preview binding failed", e)
                        cameraReady = false
                        message =
                            "Could not start camera: ${e.localizedMessage ?: "Unknown error"}"
                    }
                },
                ContextCompat.getMainExecutor(context)
            )

            onDispose {
                disposed = true
                cameraReady = false

                val provider = boundProvider
                val preview = boundPreview

                if (provider != null && preview != null) {
                    try {
                        provider.unbind(preview)
                    } catch (e: Exception) {
                        Log.w(EXPIRY_CAMERA_TAG, "Preview cleanup failed", e)
                    }
                }
            }
        }
    }

    fun resetScan() {
        detectedCrop = null
        rawOcrText = ""
        confirmedDate = ""
        saveSucceeded = false
        message = "Ready to scan. Position the expiry label clearly in view."
    }

    fun captureAndAnalyze() {
        if (!cameraGranted) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        if (!cameraReady) {
            message = "Camera is still starting. Please wait and try again."
            return
        }

        if (selectedMedication == null) {
            message = "Select the medication before scanning."
            return
        }

        if (isBusy) return

        val capturedBitmap = previewView.bitmap

        if (capturedBitmap == null) {
            message = "Camera image is not ready. Wait for the preview and try again."
            return
        }

        isBusy = true
        saveSucceeded = false
        detectedCrop = null
        rawOcrText = ""
        confirmedDate = ""
        message = "Detecting the expiry-date region…"

        scope.launch {
            try {
                val detection = withContext(Dispatchers.Default) {
                    detector.detect(context, capturedBitmap)
                }

                val detectedBitmap = detection.croppedBitmap

                if (detectedBitmap == null) {
                    message = detection.message.ifBlank {
                        "No expiry-date region detected. Improve the lighting, move closer, and scan again."
                    }
                    return@launch
                }

                // Reject tiny or invalid crops before passing them to OCR.
                Log.d(
                    EXPIRY_CAMERA_TAG,
                    "Detector crop dimensions: ${detectedBitmap.width} x ${detectedBitmap.height}"
                )

                if (
                    detectedBitmap.width < 32 ||
                    detectedBitmap.height < 32
                ) {
                    message =
                        "The detected region is too small to read. Move closer to the expiry label and scan again."
                    return@launch
                }

                // Copy the crop so the screen can display it independently.
                val crop = detectedBitmap.copy(
                    Bitmap.Config.ARGB_8888,
                    false
                )

                if (crop.width < 32 || crop.height < 32) {
                    message = "The cropped image is too small for OCR. Please scan again."
                    return@launch
                }

                detectedCrop = crop
                message = "Reading the expiry-date text…"

                Log.d(
                    EXPIRY_CAMERA_TAG,
                    "OCR input dimensions: ${crop.width} x ${crop.height}"
                )

                val recognizedText = recognizeExpiryText(crop)
                rawOcrText = recognizedText

                if (recognizedText.isBlank()) {
                    message =
                        "No readable text found. Adjust the label and try again."
                    return@launch
                }

                val parsedDate = findCompleteExpiryDate(recognizedText)

                if (parsedDate != null) {
                    confirmedDate = parsedDate
                    message =
                        "Review the detected date and correct it if necessary before saving."
                } else {
                    confirmedDate = ""
                    message =
                        "Text was detected, but no complete date was recognized. Enter the full date printed on the package. Do not guess missing parts."
                }
            } catch (e: Exception) {
                Log.e(EXPIRY_CAMERA_TAG, "Expiry scan failed", e)
                message =
                    "Scan failed: ${e.localizedMessage ?: "Please try again."}"
            } finally {
                isBusy = false
            }
        }
    }

    fun saveConfirmedDate() {
        val medication = selectedMedication

        if (medication == null) {
            message = "Select a medication first."
            return
        }

        val medicationId = medication.id

        if (medicationId.isNullOrBlank()) {
            message = "This medication has no valid ID and cannot be updated."
            return
        }

        val dateToSave = try {
            LocalDate.parse(
                confirmedDate.trim(),
                DateTimeFormatter.ISO_LOCAL_DATE
            ).toString()
        } catch (_: DateTimeParseException) {
            message = "Enter a valid date in YYYY-MM-DD format."
            return
        }

        if (isBusy) return

        isBusy = true
        saveSucceeded = false
        message = "Saving confirmed expiry date…"

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.updateMedicationExpiryDate(
                        medicationId = medicationId,
                        confirmedExpiryDate = dateToSave
                    )
                }

                medications = medications.map { item ->
                    if (item.id == medicationId) {
                        item.copy(expiryDate = dateToSave)
                    } else {
                        item
                    }
                }

                selectedMedication = medications.firstOrNull {
                    it.id == medicationId
                }

                confirmedDate = dateToSave
                saveSucceeded = true
                message = "Expiry date saved successfully."
            } catch (e: Exception) {
                Log.e(EXPIRY_CAMERA_TAG, "Could not save expiry date", e)
                message =
                    "Could not save the expiry date: ${e.localizedMessage ?: "Please try again."}"
            } finally {
                isBusy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Expiry Date") },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        enabled = !isBusy
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Select the medicine, scan its expiry label, and verify the date before saving.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Medication",
                style = MaterialTheme.typography.titleSmall
            )

            OutlinedButton(
                onClick = { medicationMenuExpanded = true },
                enabled = medications.isNotEmpty() && !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    selectedMedication?.medName
                        ?: if (medications.isEmpty()) {
                            "No medications available"
                        } else {
                            "Choose a medication"
                        }
                )
            }

            DropdownMenu(
                expanded = medicationMenuExpanded,
                onDismissRequest = { medicationMenuExpanded = false }
            ) {
                medications.forEach { medication ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(medication.medName)
                                Text(
                                    text = medication.dosage,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        onClick = {
                            selectedMedication = medication
                            medicationMenuExpanded = false
                            resetScan()
                        }
                    )
                }
            }

            selectedMedication?.expiryDate?.let { expiryDate ->
                Text(
                    text = "Current saved expiry date: $expiryDate",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black
                )
            ) {
                if (cameraGranted) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Camera permission is needed to scan the expiry label.",
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        ) {
                            Text("Grant Camera Permission")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { captureAndAnalyze() },
                    enabled = cameraGranted &&
                            cameraReady &&
                            selectedMedication != null &&
                            !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Capture & Scan")
                }

                OutlinedButton(
                    onClick = { resetScan() },
                    enabled = !isBusy
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset scan"
                    )
                }
            }

            if (isBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.size(10.dp))
                    Text("Please wait…")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (saveSucceeded) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (saveSucceeded) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            detectedCrop?.let { crop ->
                Text(
                    text = "Detected expiry-label region",
                    style = MaterialTheme.typography.titleSmall
                )

                Image(
                    bitmap = crop.asImageBitmap(),
                    contentDescription = "Detected expiry-date region",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }

            if (rawOcrText.isNotBlank()) {
                Text(
                    text = "Detected OCR text",
                    style = MaterialTheme.typography.titleSmall
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = rawOcrText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "Confirm expiry date",
                style = MaterialTheme.typography.titleSmall
            )

            OutlinedTextField(
                value = confirmedDate,
                onValueChange = {
                    confirmedDate = it
                    saveSucceeded = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Expiry date (YYYY-MM-DD)") },
                placeholder = { Text("2027-04-30") },
                singleLine = true,
                enabled = !isBusy
            )

            Text(
                text = "Check the printed package date before saving. If only month and year are printed, do not invent a day.",
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = { saveConfirmedDate() },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedMedication != null &&
                        confirmedDate.isNotBlank() &&
                        !isBusy
            ) {
                Text(
                    if (saveSucceeded) {
                        "Expiry Date Saved"
                    } else {
                        "Confirm & Save Expiry Date"
                    }
                )
            }
        }
    }
}

private suspend fun recognizeExpiryText(bitmap: Bitmap): String =
    suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result.text)
                }
                recognizer.close()
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
                recognizer.close()
            }

        continuation.invokeOnCancellation {
            recognizer.close()
        }
    }

/**
 * Accepts only a complete, valid date containing day, month, and year.
 * Returns ISO YYYY-MM-DD for the Supabase PostgreSQL date column.
 */
private fun findCompleteExpiryDate(text: String): String? {
    val patterns = listOf(
        Regex("""\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b"""),
        Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](20\d{2})\b""")
    )

    for ((index, pattern) in patterns.withIndex()) {
        for (match in pattern.findAll(text)) {
            val groups = match.groupValues

            val year: Int
            val month: Int
            val day: Int

            if (index == 0) {
                year = groups[1].toIntOrNull() ?: continue
                month = groups[2].toIntOrNull() ?: continue
                day = groups[3].toIntOrNull() ?: continue
            } else {
                day = groups[1].toIntOrNull() ?: continue
                month = groups[2].toIntOrNull() ?: continue
                year = groups[3].toIntOrNull() ?: continue
            }

            try {
                return LocalDate.of(year, month, day).toString()
            } catch (_: DateTimeParseException) {
                // Invalid date: do not guess or silently correct OCR output.
            } catch (_: Exception) {
                // Ignore invalid date values and continue searching.
            }
        }
    }

    return null
}
