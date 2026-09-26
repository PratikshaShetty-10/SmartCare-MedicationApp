package com.yourteam.smartcare.api

object ApiConfig {

    // 1. Turned OFF mock mode so it actually uses the internet
    const val MOCK_VERIFIED_SUCCESS = false
    const val USE_MOCK_AI = false

    // 2. Updated to your exact GitHub Codespaces URL + /analyze
    const val AI_VERIFY_URL = "https://orange-cod-r4vx9g9r5gwwhpjg9-5000.app.github.dev/analyze"

    const val REQUEST_TIMEOUT_MS = 15_000L
    const val MAX_RETRIES = 2

    const val MOCK_DELAY_MS = 1200L
}