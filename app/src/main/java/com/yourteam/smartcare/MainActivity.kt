package com.yourteam.smartcare

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

    override fun onDestroy() {
        voiceHelper.shutdown()
        super.onDestroy()
    }
}

@Composable
fun TestConsoleScreen() {
    val context = LocalContext.current
    val escalationManager = EscalationManager(context)
    val notificationHelper = NotificationHelper(context)

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

        Text("Note: Verification and Camera tests are handled by the UI module.",
            style = MaterialTheme.typography.bodySmall)
    }
}