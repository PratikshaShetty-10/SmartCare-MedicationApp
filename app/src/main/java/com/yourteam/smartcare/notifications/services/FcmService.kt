package com.yourteam.smartcare.notifications.services

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yourteam.smartcare.utils.NotificationHelper

class FcmService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {

        // Initialize the helper
        val notificationHelper = NotificationHelper(applicationContext)

        // Extract data from the FCM payload
        val title = message.notification?.title ?: "SmartCare Alert"
        val body = message.notification?.body ?: "Emergency detected"

        // Use the initialized helper to display the alert
        notificationHelper.showEmergencyAlarm(title, body)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // In a production app, send this new token to your Supabase "user_tokens" table
    }
}