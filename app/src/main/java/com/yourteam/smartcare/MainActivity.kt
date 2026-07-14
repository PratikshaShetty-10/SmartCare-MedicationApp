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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
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

        // Initialize core utilities
        voiceHelper = VoiceHelper(this)
        NotificationHelper(this).createChannels()

        // Schedule persistent background tasks
        scheduleBackgroundTasks()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SmartCareTestScreen()
                }
            }
        }
    }

    private fun scheduleBackgroundTasks() {
        val workManager = WorkManager.getInstance(this)

        val reminderWork = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork("smartcare_reminder", ExistingPeriodicWorkPolicy.UPDATE, reminderWork)

        val stockWork = PeriodicWorkRequestBuilder<StockCheckerWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()

        workManager.enqueueUniquePeriodicWork("smartcare_stock_check", ExistingPeriodicWorkPolicy.UPDATE, stockWork)
    }

    override fun onDestroy() {
        voiceHelper.shutdown()
        super.onDestroy()
    }
}

@Composable
fun SmartCareTestScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Manage EscalationManager lifecycle within this screen
    val escalationManager = remember { EscalationManager(context) }
    val notificationHelper = remember { NotificationHelper(context) }

    // Ensure resources are released if the screen is destroyed
    DisposableEffect(Unit) {
        onDispose { escalationManager.stopAll() }
    }

    var showCameraPreview by remember { mutableStateOf(false) }

    if (showCameraPreview) {
        val applicationContext = null
        val viewModel: VerificationViewModel = viewModel(
            factory = VerificationViewModel.Factory(applicationContext as android.app.Application, "SAMPLE_MED_ID") { verified ->
                Toast.makeText(context, if (verified) "✅ Verified!" else "❌ Failed!", Toast.LENGTH_SHORT).show()
            }
        )
        val uiState by viewModel.uiState.collectAsState()

        Box(modifier = Modifier.fillMaxSize()) {
            val controller = remember {
                LifecycleCameraController(context).apply {
                    setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
                    bindToLifecycle(lifecycleOwner)
                }
            }
            CameraControllerView(controller = controller, modifier = Modifier.fillMaxSize())

            Button(onClick = { showCameraPreview = false }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Text("⬅ Back")
            }

            Button(
                onClick = { viewModel.captureAndVerify(controller) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth(),
                enabled = uiState !is VerificationUiState.Loading
            ) {
                Text(if (uiState is VerificationUiState.Loading) "⏳ Processing..." else "📸 Capture & Verify")
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("SmartCare Test Console", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = { notificationHelper.showReminder("Test", "Notification Works!") }, modifier = Modifier.fillMaxWidth()) {
                Text("🔔 Show Notification")
            }

            Button(onClick = { escalationManager.startEscalation("Paracetamol", "SAMPLE_GUARDIAN") },
                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text("🚀 Test Auto Escalation")
            }

            Button(onClick = { escalationManager.stopAll() },
                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("⏹️ Stop All")
            }

            Button(onClick = { showCameraPreview = true }, modifier = Modifier.fillMaxWidth()) {
                Text("📸 Open Camera")
            }
        }
    }
}