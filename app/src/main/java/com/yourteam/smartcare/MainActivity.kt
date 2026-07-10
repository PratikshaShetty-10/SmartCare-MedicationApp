package com.yourteam.smartcare

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yourteam.smartcare.ui.theme.SmartCareTheme
import com.yourteam.smartcare.utils.EscalationManager
import com.yourteam.smartcare.utils.NotificationHelper
import com.yourteam.smartcare.utils.VibrationHelper
import com.yourteam.smartcare.utils.VoiceHelper
import com.yourteam.smartcare.workers.ReminderWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    // Permission launcher for Android 13+
    val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showTestNotification()
        } else {
            android.widget.Toast.makeText(
                this,
                "Please enable notifications in Settings",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Create notification channels
        val helper = NotificationHelper(this)
        helper.createChannels()

        // 2. Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 3. 🚀 SCHEDULE THE DAILY REMINDER (AUTO-TRIGGER)
        scheduleDailyReminder()

        // 4. Set the UI
        setContent {
            SmartCareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NotificationTestScreen()
                }
            }
        }
    }

    // ===== SCHEDULE THE DAILY REMINDER =====
    private fun scheduleDailyReminder() {
        val reminderWork = PeriodicWorkRequestBuilder<ReminderWorker>(
            24, TimeUnit.HOURS  // Runs every 24 hours
        ).setInitialDelay(5, TimeUnit.SECONDS) // 🔥 For testing: triggers 5 seconds after app starts
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "smartcare_reminder",
            ExistingPeriodicWorkPolicy.REPLACE, // Keep existing if already scheduled
            reminderWork
        )

        android.util.Log.d("MainActivity", "✅ Daily reminder scheduled!")
    }

    fun showTestNotification() {
        val helper = NotificationHelper(this)
        helper.showReminder("SmartCare Test", "You clicked the button!")
    }
}

@Composable
fun NotificationTestScreen() {
    val context = LocalContext.current

    // --- Permission Launcher for Notifications (Handled inside Compose) ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val helper = NotificationHelper(context)
            helper.showReminder("SmartCare Test", "You clicked the button!")
        } else {
            android.widget.Toast.makeText(
                context,
                "Please enable notifications in Settings",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            text = "SmartCare Notification Test",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // --- NOTIFICATION BUTTON ---
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val helper = NotificationHelper(context)
                        helper.showReminder("SmartCare Test", "You clicked the button!")
                    } else {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    val helper = NotificationHelper(context)
                    helper.showReminder("SmartCare Test", "You clicked the button!")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text(text = "🔔 Show Notification", style = MaterialTheme.typography.titleMedium)
        }

        // --- SPACER ---
        Spacer(modifier = Modifier.height(16.dp))

        // --- VIBRATION BUTTON ---
        Button(
            onClick = {
                android.widget.Toast.makeText(
                    context,
                    "Vibration triggered!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                val vibrator = VibrationHelper(context)
                vibrator.vibrate(1000)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text(text = "📳 Vibrate Phone", style = MaterialTheme.typography.titleMedium)
        }

        // --- SPACER ---
        Spacer(modifier = Modifier.height(16.dp))

        // --- SPEAK BUTTON ---
        Button(
            onClick = {
                android.widget.Toast.makeText(
                    context,
                    "Speak button clicked!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                val voice = VoiceHelper(context)
                voice.speak("Hello! This is SmartCare speaking.")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Text(text = "🔊 Speak Test", style = MaterialTheme.typography.titleMedium)
        }

        // --- SPACER ---
        Spacer(modifier = Modifier.height(16.dp))

        // --- ESCALATION LEVEL 1 BUTTON ---
        Button(
            onClick = {
                val escalation = EscalationManager(context)
                escalation.escalate(1, "Paracetamol")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = "🔵 Level 1 (Soft Reminder)", style = MaterialTheme.typography.titleSmall)
        }

        // --- ESCALATION LEVEL 2 BUTTON ---
        Button(
            onClick = {
                val escalation = EscalationManager(context)
                escalation.escalate(2, "Paracetamol")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = "🟠 Level 2 (Urgent + Voice)", style = MaterialTheme.typography.titleSmall)
        }

        // --- ESCALATION LEVEL 3 BUTTON ---
        Button(
            onClick = {
                val escalation = EscalationManager(context)
                escalation.escalate(3, "Paracetamol")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = "🔴 Level 3 (EMERGENCY ALARM)", style = MaterialTheme.typography.titleSmall)
        }

        // --- STOP ALARM BUTTON ---
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val escalation = EscalationManager(context)
                escalation.stopAll()
                android.widget.Toast.makeText(
                    context,
                    "Alarm stopped!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = "⏹️ Stop Alarm", style = MaterialTheme.typography.titleSmall)
        }

        Text(
            text = "Click the buttons to test features",
            modifier = Modifier.padding(top = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}