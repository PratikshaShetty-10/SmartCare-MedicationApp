package com.yourteam.smartcare.escalation

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class DosePopupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow the medication popup to appear when the screen is locked.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)

            val keyguardManager =
                getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID)
        val medName = intent.getStringExtra(EXTRA_MED_NAME) ?: "Medication"
        val dosage = intent.getStringExtra(EXTRA_DOSAGE).orEmpty()

        if (medicationId.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🚨 Medication Reminder",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = medName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (dosage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = dosage,
                            fontSize = 17.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Please take your medicine.",
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val acknowledged = DoseAcknowledger.acknowledge(
                                this@DosePopupActivity,
                                medicationId
                            )

                            if (acknowledged) {
                                finish()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "TAKEN",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_MEDICATION_ID =
            "com.yourteam.smartcare.escalation.EXTRA_MEDICATION_ID"

        const val EXTRA_MED_NAME =
            "com.yourteam.smartcare.escalation.EXTRA_MED_NAME"

        const val EXTRA_DOSAGE =
            "com.yourteam.smartcare.escalation.EXTRA_DOSAGE"
    }
}