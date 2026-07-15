package com.yourteam.smartcare.logic

import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourteam.smartcare.viewmodels.VerificationViewModel

@Composable
fun CameraImplementation(
    medicationId: String,
    onVerificationComplete: (Boolean) -> Unit,
    onCapture: () -> Unit  // UI calls this, we need to trigger ViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 🔥 Get the ViewModel
    val applicationContext = null
    val viewModel: VerificationViewModel = viewModel(
        factory = VerificationViewModel.Factory(applicationContext as android.app.Application, medicationId, onVerificationComplete)
    )

    // Camera Controller
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            bindToLifecycle(lifecycleOwner)
        }
    }

    // 🎯 THE BRIDGE: When UI calls onCapture(), trigger ViewModel
    // But we can't modify onCapture directly.
    // We just use a LaunchedEffect to handle the call.
    // Actually, the UI will call onCapture(). We need to define what happens.
    // Since onCapture is passed by the UI, we just let it be.
    // The UI will call onCapture() which is defined in the NavGraph.

    // BUT the UI developer expects onCapture to trigger the camera capture.
    // So the UI will call onCapture() and we need to capture the image.
    // The UI doesn't know about the controller, so we need to pass the controller to the UI.

    // 💡 SIMPLEST: Just expose the ViewModel's function to the UI.
    // Let the UI call viewModel.captureAndVerify(controller) directly.
    // This is actually the cleanest way.

    // So we pass the controller to the UI via the onCapture lambda.
    // But onCapture is () -> Unit, it doesn't take parameters.

    // FINAL SIMPLE SOLUTION: Let the UI call viewModel.captureAndVerify(controller).
    // The UI will handle the button click and call that function.

    // So you don't even need to do anything here except show the camera preview.
    CameraControllerView(
        controller = controller,
        modifier = Modifier.fillMaxSize()
    )
}