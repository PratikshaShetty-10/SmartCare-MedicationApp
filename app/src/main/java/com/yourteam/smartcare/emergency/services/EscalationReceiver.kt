package com.yourteam.smartcare.emergency.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class EscalationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getStringExtra("MEDICATION_ID") ?: ""

        val serviceIntent = Intent(context, EscalationForegroundService::class.java).apply {
            putExtra("MEDICATION_ID", medicationId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}