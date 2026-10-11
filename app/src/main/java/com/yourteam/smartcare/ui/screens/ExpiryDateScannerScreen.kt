
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
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val EXPIRY_CAMERA_TAG = "ExpiryCamera"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryDateScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val repository = remember { MedicationRepository() }
    val detector = remember { ExpiryDateRegionDetector() }

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
    var medications by remember {
        mutableStateOf<List<Medication>>(emptyList())
    }
    var selectedMedication by remember {
        mutableStateOf<Medication?>(null)
    }
    var medicationMenuExpanded by remember { mutableStateOf(false) }

    var detectedCrop by remember { mutableStateOf<Bitmap?>(null) }
    var confirmedDate by remember { mutableStateOf("") }
    var message by remember {
        mutableStateOf("Select a medication to begin scanning.")
    }

    var isBusy by remember { mutableStateOf(false) }
    var saveSucceeded by remember { mutableStateOf(false) }
    var dateDetected by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
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
            message = "Could not load medications: " +
                    (e.localizedMessage ?: "Please try again.")
        }
    }

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

                        Log.d(
                            EXPIRY_CAMERA_TAG,
                            "Camera preview bound successfully"
                        )

                        message =
                            "Camera ready. Position the expiry label in view."
                    } catch (e: Exception) {
                        Log.e(
                            EXPIRY_CAMERA_TAG,
                            "Camera preview binding failed",
                            e
                        )

                        cameraReady = false
                        message = "Could not start camera: " +
                                (e.localizedMessage ?: "Unknown error")
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
                        Log.w(
                            EXPIRY_CAMERA_TAG,
                            "Preview cleanup failed",
                            e
                        )
                    }
                }
            }
        }
    }

    fun resetScan() {
        detectedCrop = null
        confirmedDate = ""
        dateDetected = false
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
            message =
                "Camera image is not ready. Wait for the preview and try again."
            return
        }

        isBusy = true
        saveSucceeded = false
        dateDetected = false
        detectedCrop = null
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
                        "No expiry-date region detected. Improve lighting and scan again."
                    }
                    return@launch
                }

                Log.d(
                    EXPIRY_CAMERA_TAG,
                    "Detector crop dimensions: " +
                            "${detectedBitmap.width} x ${detectedBitmap.height}"
                )

                if (detectedBitmap.width < 32 ||
                    detectedBitmap.height < 32
                ) {
                    message =
                        "The detected region is too small. Move closer and scan again."
                    return@launch
                }

                val crop = detectedBitmap.copy(
                    Bitmap.Config.ARGB_8888,
                    false
                )

                detectedCrop = crop

                Log.d(
                    EXPIRY_CAMERA_TAG,
                    "OCR input dimensions: ${crop.width} x ${crop.height}"
                )

                message = "Reading the expiry date…"

                val recognizedText = recognizeExpiryText(crop)

                Log.d(EXPIRY_CAMERA_TAG, "OCR text: $recognizedText")

                if (recognizedText.isBlank()) {
                    message =
                        "No readable text found. Adjust the label and scan again."
                    return@launch
                }

                val parsedDate = findCompleteExpiryDate(recognizedText)

                Log.d(
                    EXPIRY_CAMERA_TAG,
                    "Parsed expiry date: $parsedDate"
                )

                if (parsedDate != null) {
                    confirmedDate = parsedDate
                    dateDetected = true

                    val status = getExpiryStatus(parsedDate)

                    message = when (status) {
                        ExpiryStatus.EXPIRED ->
                            "Expired date detected. Do not use this medicine. Verify the printed date before saving."

                        ExpiryStatus.WITHIN_EXPIRY ->
                            "Expiry date detected. Verify the date before saving."

                        ExpiryStatus.INVALID ->
                            "The expiry date could not be verified."
                    }
                } else {
                    confirmedDate = ""
                    dateDetected = false
                    message =
                        "Could not reliably identify a date next to an expiry label. Adjust the label and scan again."
                }
            } catch (e: Exception) {
                Log.e(EXPIRY_CAMERA_TAG, "Expiry scan failed", e)
                message = "Scan failed: " +
                        (e.localizedMessage ?: "Please try again.")
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

        if (!dateDetected || confirmedDate.isBlank()) {
            message = "Scan and detect a valid expiry date first."
            return
        }

        val dateToSave = try {
            LocalDate.parse(confirmedDate).toString()
        } catch (e: DateTimeParseException) {
            message = "The detected date is invalid. Please scan again."
            return
        }

        if (isBusy || saveSucceeded) return

        val statusBeforeSave = getExpiryStatus(dateToSave)

        isBusy = true
        message = if (statusBeforeSave == ExpiryStatus.EXPIRED) {
            "Saving expired date. Do not use this medicine."
        } else {
            "Saving expiry date…"
        }

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.updateMedicationExpiryDate(
                        medicationId = medicationId,
                        confirmedExpiryDate = dateToSave
                    )
                }

                val updatedMedications = medications.map { item ->
                    if (item.id == medicationId) {
                        item.copy(expiryDate = dateToSave)
                    } else {
                        item
                    }
                }

                medications = updatedMedications
                selectedMedication = updatedMedications.firstOrNull {
                    it.id == medicationId
                }

                confirmedDate = dateToSave
                saveSucceeded = true

                message = when (getExpiryStatus(dateToSave)) {
                    ExpiryStatus.EXPIRED ->
                        "Expiry date saved. This medicine is expired — do not use it."

                    ExpiryStatus.WITHIN_EXPIRY ->
                        "Expiry date saved successfully. It is within its printed expiry date."

                    ExpiryStatus.INVALID ->
                        "Date saved, but its expiry status could not be verified."
                }

                Log.d(
                    EXPIRY_CAMERA_TAG,
                    "Saved expiry date for medication ID=$medicationId: $dateToSave; status=${getExpiryStatus(dateToSave)}"
                )
            } catch (e: Exception) {
                Log.e(
                    EXPIRY_CAMERA_TAG,
                    "Could not save expiry date",
                    e
                )

                message = "Could not save expiry date: " +
                        (e.localizedMessage ?: "Please try again.")
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
                text = "Select a medicine and scan its expiry label.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Choose Medication",
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
                val savedStatus = getExpiryStatus(expiryDate)

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Currently saved expiry date: $expiryDate",
                        style = MaterialTheme.typography.bodySmall
                    )

                    ExpiryStatusText(status = savedStatus)
                }
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
                                cameraPermissionLauncher.launch(
                                    Manifest.permission.CAMERA
                                )
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
                        contentDescription = "Refresh scan"
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Refresh")
                }
            }

            if (isBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp)
                    )
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
                    text = "Detected label region",
                    style = MaterialTheme.typography.titleSmall
                )

                Image(
                    bitmap = crop.asImageBitmap(),
                    contentDescription = "Detected expiry label",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }

            if (dateDetected && confirmedDate.isNotBlank()) {
                val expiryStatus = getExpiryStatus(confirmedDate)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (expiryStatus) {
                            ExpiryStatus.EXPIRED ->
                                MaterialTheme.colorScheme.errorContainer

                            ExpiryStatus.WITHIN_EXPIRY ->
                                MaterialTheme.colorScheme.secondaryContainer

                            ExpiryStatus.INVALID ->
                                MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Detected Expiry Date",
                            style = MaterialTheme.typography.titleSmall
                        )

                        Text(
                            text = confirmedDate,
                            style = MaterialTheme.typography.headlineSmall
                        )

                        ExpiryStatusText(status = expiryStatus)




                    }
                }

                Button(
                    onClick = { saveConfirmedDate() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy && !saveSucceeded
                ) {
                    Text(
                        if (saveSucceeded) {
                            "Expiry Date Saved"
                        } else {
                            "Confirm & Save"
                        }
                    )
                }
            }
        }
    }
}

private enum class ExpiryStatus {
    EXPIRED,
    WITHIN_EXPIRY,
    INVALID
}

private fun getExpiryStatus(expiryDate: String): ExpiryStatus {
    return try {
        val date = LocalDate.parse(expiryDate)

        if (date.isBefore(LocalDate.now())) {
            ExpiryStatus.EXPIRED
        } else {
            ExpiryStatus.WITHIN_EXPIRY
        }
    } catch (e: DateTimeParseException) {
        Log.w(
            EXPIRY_CAMERA_TAG,
            "Could not calculate expiry status for: $expiryDate",
            e
        )
        ExpiryStatus.INVALID
    }
}

@Composable
private fun ExpiryStatusText(status: ExpiryStatus) {
    val label: String
    val color: Color

    when (status) {
        ExpiryStatus.EXPIRED -> {
            label = "EXPIRED — DO NOT USE"
            color = MaterialTheme.colorScheme.error
        }

        ExpiryStatus.WITHIN_EXPIRY -> {
            label = "WITHIN EXPIRY DATE"
            color = Color(0xFF2E7D32)
        }

        ExpiryStatus.INVALID -> {
            label = "UNABLE TO VERIFY EXPIRY"
            color = MaterialTheme.colorScheme.onSurface
        }
    }

    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.titleMedium
    )
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
 * Extracts a date associated with an explicit expiry label.
 *
 * Supports:
 * YYYY-MM-DD, YYYY/MM/DD, YYYY.MM.DD
 * DD-MM-YYYY, DD/MM/YYYY, DD.MM.YYYY
 * DD-MM-YY, DD/MM/YY, DD.MM.YY
 * MM/YYYY, MM-YYYY, MM.YYYY
 * MM/YY, MM-YY, MM.YY
 *
 * Month/year values are converted to the final day of that month.
 * Returns ISO YYYY-MM-DD for the repository.
 *
 * If an explicit expiry label is present but its date is unreadable,
 * the parser returns null instead of guessing from a manufacture date.
 */
private fun findCompleteExpiryDate(text: String): String? {
    val lines = text
        .replace('\r', '\n')
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (lines.isEmpty()) return null

    val expiryLabel = Regex(
        """(?i)\bEXP(?:IRY)?(?:\s*\.?\s*DATE)?\b"""
    )

    val excludedLabels = Regex(
        """(?i)\b(?:MFG|MFD|MANUFACTURED|MRP|B\.?\s*NO|BATCH|PKD|PACKED)\b"""
    )

    for (index in lines.indices) {
        val line = lines[index]
        val labelMatch = expiryLabel.find(line) ?: continue

        // Reject an expiry label that is actually fused with a number,
        // such as EXP2/2020. There is no word boundary between P and 2,
        // so such a string will not match expiryLabel.
        val candidate = line.substring(labelMatch.range.last + 1)
            .trim()
            .trimStart(':', '#', '-', '.', ' ')

        if (candidate.isNotBlank()) {
            val parsed = parseDateCandidate(candidate)

            if (parsed != null) {
                Log.d(
                    EXPIRY_CAMERA_TAG,
                    "Date parsed from expiry-labelled text: $candidate -> $parsed"
                )
                return parsed
            }
        }

        // Handle OCR where the label and date are on separate lines.
        if (candidate.isBlank()) {
            val nextLine = lines.getOrNull(index + 1)

            if (nextLine != null &&
                !excludedLabels.containsMatchIn(nextLine) &&
                !expiryLabel.containsMatchIn(nextLine)
            ) {
                val parsed = parseDateCandidate(nextLine)

                if (parsed != null) {
                    Log.d(
                        EXPIRY_CAMERA_TAG,
                        "Date parsed from line after expiry label: $nextLine -> $parsed"
                    )
                    return parsed
                }
            }
        }

        // An expiry label was found, but its date was not reliable.
        // Do not accidentally save a date from another package field.
        Log.w(
            EXPIRY_CAMERA_TAG,
            "Expiry label found but no valid date was detected: $line"
        )
        return null
    }

    // Without an explicit expiry label, the scanner cannot safely know
    // whether a detected number belongs to MFG, BATCH, or EXP.
    Log.w(EXPIRY_CAMERA_TAG, "No explicit expiry label found.")
    return null
}

private fun parseDateCandidate(text: String): String? {
    val candidate = text.trim()

    if (candidate.isBlank()) return null

    val yearFirst = Regex(
        """(?<!\d)(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)"""
    )

    val dayFirst = Regex(
        """(?<!\d)(\d{1,2})[-/.](\d{1,2})[-/.](\d{2}|\d{4})(?!\d)"""
    )

    val monthYear = Regex(
        """(?<!\d)(0?[1-9]|1[0-2])\s*[/.-]\s*(\d{2}|\d{4})(?!\d)"""
    )

    fun parseYear(value: String): Int? {
        val number = value.toIntOrNull() ?: return null

        return when (value.length) {
            2 -> if (number <= 79) 2000 + number else 1900 + number
            4 -> if (number in 2000..2099) number else null
            else -> null
        }
    }

    // Full year-first dates.
    for (match in yearFirst.findAll(candidate)) {
        val year = match.groupValues[1].toIntOrNull() ?: continue
        val month = match.groupValues[2].toIntOrNull() ?: continue
        val day = match.groupValues[3].toIntOrNull() ?: continue

        try {
            return LocalDate.of(year, month, day).toString()
        } catch (_: DateTimeException) {
            // Invalid dates are rejected.
        }
    }

    // Full day-first dates.
    for (match in dayFirst.findAll(candidate)) {
        val day = match.groupValues[1].toIntOrNull() ?: continue
        val month = match.groupValues[2].toIntOrNull() ?: continue
        val year = parseYear(match.groupValues[3]) ?: continue

        try {
            return LocalDate.of(year, month, day).toString()
        } catch (_: DateTimeException) {
            // Invalid dates are rejected.
        }
    }

    // Month/year expiry dates, for example 10/2025.
    for (match in monthYear.findAll(candidate)) {
        val month = match.groupValues[1].toIntOrNull() ?: continue
        val year = parseYear(match.groupValues[2]) ?: continue

        try {
            return YearMonth.of(year, month)
                .atEndOfMonth()
                .toString()
        } catch (_: DateTimeException) {
            // Invalid month/year values are rejected.
        }
    }

    return null
}
