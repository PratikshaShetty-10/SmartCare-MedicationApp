package com.yourteam.smartcare.logic

import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraControllerView(
    controller: LifecycleCameraController,
    modifier: Modifier = Modifier
) {
    // TEMPORARY STUB - Will be replaced when teammate pushes code
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
            }
        },
        modifier = modifier
    )
}