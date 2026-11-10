package com.yourteam.smartcare.notifications.services

import android.util.Log

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yourteam.smartcare.api.GuardianNotifier
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.escalation.ScheduleSync
import com.yourteam.smartcare.utils.NotificationHelper

import io.github.jan.supabase.auth.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {

        Log.d(
            "FcmService",
            "FCM received: type=${message.data["type"]}"
        )

        /*
         * Guardian changed the Elder's medication schedule.
         *
         * This is a silent data-only FCM message.
         * It must NOT show a notification.
         * It only triggers an immediate schedule synchronization.
         */
        if (message.data["type"] == "medication_schedule_updated") {

            Log.d(
                "FcmService",
                "Medication schedule update received. Enqueuing schedule sync."
            )

            ScheduleSync.enqueueNow(applicationContext)

            return
        }

        /*
         * Medication missed-dose alert.
         *
         * This is a normal guardian notification.
         * It is NOT an SOS and must NOT open the emergency
         * full-screen flow.
         */
        if (message.data["type"] == "missed_dose") {

            Log.d(
                "FcmService",
                "Missed-dose FCM received. Showing guardian alert."
            )

            val notificationHelper =
                NotificationHelper(applicationContext)

            notificationHelper.showGuardianMissedAlert(
                message.data["title"]
                    ?: "⚠️ Medication Missed",

                message.data["body"]
                    ?: "A scheduled medication was not acknowledged."
            )

            return
        }

        /*
         * Existing SmartCare behaviour.
         *
         * SOS/emergency notifications continue using the
         * existing emergency notification flow.
         */
        Log.d(
            "FcmService",
            "Unknown/non-medication FCM type. Using emergency notification flow."
        )

        val notificationHelper =
            NotificationHelper(applicationContext)

        val title =
            message.notification?.title
                ?: "SmartCare Alert"

        val body =
            message.notification?.body
                ?: "Emergency detected"

        notificationHelper.showEmergencyAlarm(
            title,
            body
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        /*
         * Firebase may generate a new token when the app is
         * installed, restored, updated, or when Firebase
         * invalidates/refreshes the previous token.
         *
         * Save the CURRENT token to the currently authenticated
         * user's profile so that both Guardian and Elder devices
         * can receive FCM correctly.
         *
         * Do NOT log the actual token.
         */
        Log.d(
            "FcmService",
            "FCM token refreshed. Updating profile token."
        )

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val user = SupabaseClient.client.auth.currentSessionOrNull()?.user

                if (user == null) {

                    Log.w(
                        "FcmService",
                        "No authenticated user available. Token not saved."
                    )

                    return@launch
                }

                val notifier =
                    GuardianNotifier(applicationContext)

                notifier.saveGuardianToken(
                    user.id,
                    token
                )

                Log.d(
                    "FcmService",
                    "FCM token updated for current user."
                )

            } catch (e: Exception) {

                Log.e(
                    "FcmService",
                    "Failed to update FCM token.",
                    e
                )
            }
        }
    }
}