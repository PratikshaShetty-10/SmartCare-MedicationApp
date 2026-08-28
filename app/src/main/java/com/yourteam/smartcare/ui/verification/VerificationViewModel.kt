package com.yourteam.smartcare.ui.verification

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourteam.smartcare.SmartCareApp
import com.yourteam.smartcare.api.GuardianNotifier
import com.yourteam.smartcare.emergency.services.EscalationManager
import com.yourteam.smartcare.repositories.VerificationRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume

@Serializable
data class PatientGuardianRelation(
    val guardian_id: String
)

class VerificationViewModel(
    application: Application,
    private val medicationId: String,
    private val repository: VerificationRepository,
    private val onComplete: (Boolean) -> Unit
) : AndroidViewModel(application) {

    private val context = getApplication<Application>()
    private val escalationManager = EscalationManager(context)
    private val guardianNotifier = GuardianNotifier(context)

    private val _uiState = MutableStateFlow<VerificationUiState>(VerificationUiState.Idle)
    val uiState: StateFlow<VerificationUiState> = _uiState

    fun captureAndVerify(controller: LifecycleCameraController) {
        if (_uiState.value is VerificationUiState.Loading) return

        viewModelScope.launch {
            _uiState.value = VerificationUiState.Loading
            try {
                val imageBitmap = captureImage(controller)
                if (imageBitmap == null) {
                    _uiState.value = VerificationUiState.Error("Failed to capture image.")
                    return@launch
                }

                val response = repository.verifyMedication(imageBitmap, medicationId)

                if (response.verified) {
                    escalationManager.stopAll()
                    _uiState.value = VerificationUiState.Success("✅ ${response.message}")
                    onComplete(true)
                } else {
                    escalationManager.stopAll()

                    // Dynamically fetch the real guardian assigned to the active user session
                    val guardianId = fetchAssignedGuardianId() ?: "default_guardian_id"

                    guardianNotifier.notifyGuardian(guardianId, "⚠️ Failed!", "Result: ${response.message}")
                    _uiState.value = VerificationUiState.Error("❌ ${response.message}")
                    onComplete(false)
                }
            } catch (e: Exception) {
                _uiState.value = VerificationUiState.Error("Error: ${e.localizedMessage}")
                onComplete(false)
            }
        }
    }

    private suspend fun fetchAssignedGuardianId(): String? {
        return try {
            val currentUser = SmartCareApp.supabase.auth.currentUserOrNull() ?: return null
            val relations = SmartCareApp.supabase
                .from("patient_guardians")
                .select {
                    filter { eq("patient_id", currentUser.id) }
                }
                .decodeList<PatientGuardianRelation>()

            relations.firstOrNull()?.guardian_id
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun captureImage(controller: LifecycleCameraController): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            controller.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = imageProxyToBitmap(image)
                        image.close()
                        if (continuation.isActive) continuation.resume(bitmap)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            )
        }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val rotation = image.imageInfo.rotationDegrees
            if (rotation == 0) rawBitmap
            else {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            }
        } catch (e: Exception) { null }
    }

    class Factory(
        private val application: Application,
        private val medicationId: String,
        private val repository: VerificationRepository,
        private val onComplete: (Boolean) -> Unit
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VerificationViewModel(application, medicationId, repository, onComplete) as T
        }
    }
}