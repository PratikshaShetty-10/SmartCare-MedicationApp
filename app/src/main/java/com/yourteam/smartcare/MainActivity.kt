package com.yourteam.smartcare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.smartcare.logic.CameraControllerView
import com.yourteam.smartcare.receivers.BootReceiver
import com.yourteam.smartcare.utils.*
import com.yourteam.smartcare.viewmodels.SOSViewModel
import com.yourteam.smartcare.viewmodels.VerificationUiState
import com.yourteam.smartcare.viewmodels.VerificationViewModel
import com.yourteam.smartcare.workers.ReminderWorker
import com.yourteam.smartcare.workers.StockCheckerWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    lateinit var voiceHelper: VoiceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceHelper = VoiceHelper(this)
        val notificationHelper = NotificationHelper(this)
        notificationHelper.createChannels()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request permissions in production
            }
        }

        scheduleDailyReminder()
        scheduleStockChecker() // ✅ Added

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartCareTestScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceHelper.shutdown()
    }

    private fun scheduleDailyReminder() {
        val reminderWork = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "smartcare_reminder",
            ExistingPeriodicWorkPolicy.REPLACE,
            reminderWork
        )
    }

    // ✅ NEW: Schedule Stock Checker
    private fun scheduleStockChecker() {
        val stockWork = PeriodicWorkRequestBuilder<StockCheckerWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "smartcare_stock_check",
            ExistingPeriodicWorkPolicy.REPLACE,
            stockWork
        )
    }
}

@Composable
fun SmartCareTestScreen() {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    var showCameraPreview by remember { mutableStateOf(false) }

    // ─── PERMISSION LAUNCHERS ───
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCameraPreview = true
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val sosViewModel = SOSViewModel(context, "SAMPLE_ELDER_ID")
            sosViewModel.triggerSOS()
        } else {
            Toast.makeText(context, "Location permission required for SOS", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── CAMERA PREVIEW SCREEN ───
    if (showCameraPreview) {
        val viewModel: VerificationViewModel = viewModel(
            factory = VerificationViewModel.Factory(
                context,
                "SAMPLE_MED_ID",
                onComplete = { verified ->
                    if (verified) {
                        Toast.makeText(context, "✅ Medicine Verified!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "❌ Verification Failed!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        )

        val uiState by viewModel.uiState.collectAsState()

        Box(modifier = Modifier.fillMaxSize()) {
            val controller = remember {
                LifecycleCameraController(context).apply {
                    setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
                    bindToLifecycle(lifecycleOwner)
                }
            }

            CameraControllerView(
                controller = controller,
                modifier = Modifier.fillMaxSize()
            )

            when (uiState) {
                is VerificationUiState.Loading -> {
                    Text(
                        text = "⏳ Verifying... Please wait.",
                        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                is VerificationUiState.Success -> {
                    Text(
                        text = "✅ ${(uiState as VerificationUiState.Success).message}",
                        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                is VerificationUiState.Error -> {
                    Text(
                        text = "❌ ${(uiState as VerificationUiState.Error).message}",
                        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {}
            }

            Button(
                onClick = { showCameraPreview = false },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Text("⬅ Back")
            }

            Button(
                onClick = { viewModel.captureAndVerify(controller) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(0.8f)
                    .height(60.dp),
                enabled = uiState !is VerificationUiState.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState is VerificationUiState.Loading)
                        MaterialTheme.colorScheme.surfaceVariant
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (uiState is VerificationUiState.Loading) "⏳ Processing..." else "📸 Capture & Verify",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    } else {
        // ─── MAIN TEST CONSOLE ───
        val notificationHelper = remember { NotificationHelper(context) }
        val vibrationHelper = remember { VibrationHelper(context) }
        val escalationManager = remember { EscalationManager(context) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("SmartCare Test Console", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))

            // ─── WEEK 1: NOTIFICATIONS ───
            Button(onClick = { notificationHelper.showReminder("Test", "Notification Works!") }, modifier = Modifier.fillMaxWidth()) {
                Text("🔔 Show Notification")
            }
            Button(onClick = { activity?.voiceHelper?.speak("Testing voice system.") }, modifier = Modifier.fillMaxWidth()) {
                Text("🔊 Speak Test")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { escalationManager.escalate(2, "Paracetamol") }, modifier = Modifier.fillMaxWidth()) {
                Text("🟠 Level 2 (Voice)")
            }
            Button(onClick = { escalationManager.escalate(3, "Paracetamol") }, modifier = Modifier.fillMaxWidth()) {
                Text("🔴 Level 3 (Alarm)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                val receiver = BootReceiver()
                val intent = Intent(context, BootReceiver::class.java)
                intent.action = Intent.ACTION_BOOT_COMPLETED
                receiver.onReceive(context, intent)
                Toast.makeText(context, "BootReceiver triggered manually!", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth()) {
                Text("🚀 Force Test BootReceiver")
            }
            Button(onClick = { escalationManager.stopAll() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                Text("⏹️ Stop All")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                escalationManager.startEscalation("Paracetamol")
                Toast.makeText(context, "🚀 Started! Level 1 now, Level 2 in 5 min, Level 3 in 10 min", Toast.LENGTH_LONG).show()
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text("🚀 Test Auto Escalation (5min→10min)")
            }

            // ─── WEEK 2: CAMERA ───
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text("📸 Week 2: Verification", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    showCameraPreview = true
                } else {
                    cameraLauncher.launch(Manifest.permission.CAMERA)
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text("📸 Test Camera Preview")
            }
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    showCameraPreview = true
                } else {
                    cameraLauncher.launch(Manifest.permission.CAMERA)
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text("🔄 Test Full Verification")
            }

            // ─── WEEK 3: SOS ───
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text("🆘 Week 3: SOS", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    SOSViewModel(context, "SAMPLE_ELDER_ID").triggerSOS()
                } else {
                    locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text("🆘 Test SOS (GPS + Guardian Alert)", style = MaterialTheme.typography.titleMedium)
            }

            // ─── 📦 WEEK 4: STOCK CHECKER ───
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text("📦 Week 4: Stock", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val stockWork = OneTimeWorkRequestBuilder<StockCheckerWorker>().build()
                    WorkManager.getInstance(context).enqueue(stockWork)
                    Toast.makeText(context, "📦 Stock check triggered!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Text("📦 Test Stock Checker")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Tap buttons to test features",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}