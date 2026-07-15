package com.yourteam.smartcare.ui.verification

import android.app.Application
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.smartcare.SmartCareApp
import com.yourteam.smartcare.repositories.VerificationRepository
import kotlinx.serialization.json.Json

@Composable
fun CameraImplementation(
    medicationId: String,
    onVerificationComplete: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val application = context.applicationContext as Application

    // Uses the standard HttpClient and Json configuration from SmartCareApp
    val repository = remember {
        VerificationRepository(
            client = SmartCareApp.httpClient,
            json = Json { ignoreUnknownKeys = true }
        )
    }

    val viewModel: VerificationViewModel = viewModel(
        factory = VerificationViewModel.Factory(
            application,
            medicationId,
            repository,
            onVerificationComplete
        )
    )

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            bindToLifecycle(lifecycleOwner)
        }
    }

    CameraControllerView(
        controller = controller,
        modifier = Modifier.fillMaxSize()
    )
}