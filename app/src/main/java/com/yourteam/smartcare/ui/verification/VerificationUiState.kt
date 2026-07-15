package com.yourteam.smartcare.ui.verification

sealed interface VerificationUiState {
    object Idle : VerificationUiState
    object Loading : VerificationUiState
    data class Success(val message: String) : VerificationUiState
    data class Error(val message: String) : VerificationUiState
}