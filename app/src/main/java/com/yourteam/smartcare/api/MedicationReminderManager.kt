package com.yourteam.smartcare.api

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yourteam.smartcare.data.model.Medication
import java.util.Calendar

// 1. THIS CATCHES THE ALARM AND SHOWS THE NOTIFICATION
class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medName = intent.getStringExtra("MED_NAME") ?: "your medication"
        val dosage = intent.getStringExtra("MED_DOSAGE") ?: ""
        val medId = intent.getStringExtra("MED_ID") ?: ""

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "medication_reminders"

        // Create Android Notification Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Medication Reminders",
                NotificationManager.IMPORTANCE_HIGH // Makes it pop up on screen and ring
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Build the Notification
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💊 Time for your medication!")
            .setContentText("Please take $dosage of $medName now.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // Show the Notification
        notificationManager.notify(medId.hashCode(), builder.build())
        Log.d("MedicationAlarm", "✅ Notification fired for $medName")
    }
}

// 2. THIS CALCULATES THE TIME AND SETS THE ALARM
class MedicationReminderManager(private val context: Context) {

    fun scheduleAlarmsForToday(medications: List<Medication>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (med in medications) {
            try {
                // Parse "HH:mm" using camelCase scheduledTime
                val timeParts = med.scheduledTime.split(":")
                if (timeParts.size != 2) continue

                val hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()

                val calendar = Calendar.getInstance().apply {
                    timeInMillis = System.currentTimeMillis()
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }

                // Skip if time has passed
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    continue
                }

                val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                    putExtra("MED_NAME", med.medName) // camelCase
                    putExtra("MED_DOSAGE", med.dosage)
                    putExtra("MED_ID", med.id ?: "")
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    med.id?.hashCode() ?: med.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Set exact alarm
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )

                Log.d("MedicationAlarm", "⏰ Scheduled alarm for ${med.medName} at ${med.scheduledTime}")

            } catch (e: Exception) {
                Log.e("MedicationAlarm", "❌ Failed to parse time: ${med.scheduledTime}")
            }
        }
    }
}