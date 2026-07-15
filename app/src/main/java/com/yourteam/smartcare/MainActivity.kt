package com.yourteam.smartcare

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yourteam.smartcare.emergency.services.EscalationManager
import com.yourteam.smartcare.utils.NotificationHelper
import com.yourteam.smartcare.utils.VoiceHelper

class MainActivity : ComponentActivity() {
    private lateinit var voiceHelper: VoiceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceHelper = VoiceHelper(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TestConsoleScreen()
                }
            }
        }
    }
}

@Composable
fun TestConsoleScreen() {
    val context = LocalContext.current
    val escalationManager = EscalationManager(context)
    val notificationHelper = NotificationHelper(context)

    // Permission Launcher for Android 13+ (Notifications) and Location
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (!granted) Toast.makeText(context, "Permissions Denied", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Backend Feature Test Console", style = MaterialTheme.typography.headlineSmall)

        Button(onClick = { notificationHelper.showReminder("Stock Alert", "Only 3 days left!") }) {
            Text("Test: Stock Notification")
        }

        Button(onClick = { escalationManager.startEscalation("Ibuprofen", "GUARDIAN_123") }) {
            Text("Test: Auto Escalation Service")
        }

        Button(onClick = { escalationManager.stopAll() }) {
            Text("Test: Stop All Services")
        }
    }
}