package com.yourteam.smartcare.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yourteam.smartcare.api.NotificationClient
import com.yourteam.smartcare.emergency.services.EscalationManager

class SOSViewModelFactory(
    private val application: Application,
    private val elderId: String,
    private val guardianUserId: String,
    private val locationViewModel: LocationViewModel,
    private val notificationClient: NotificationClient,
    private val escalationManager: EscalationManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SOSViewModel(
            application, elderId, guardianUserId,
            locationViewModel, notificationClient, escalationManager
        ) as T
    }
}