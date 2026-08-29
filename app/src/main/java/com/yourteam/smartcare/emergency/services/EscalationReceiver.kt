package com.yourteam.smartcare.emergency.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class EscalationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medName = intent.getStringExtra("MED_NAME") ?: "Medication"
        val guardianId = intent.getStringExtra("GUARDIAN_ID") ?: "DEFAULT"

        val serviceIntent = Intent(context, EscalationForegroundService::class.java).apply {
            putExtra("MED_NAME", medName)
            putExtra("GUARDIAN_ID", guardianId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}