package com.yourteam.smartcare.utils

/**
 * Single source of truth for the AI verification endpoint.
 *
 * WHY THIS FILE EXISTS:
 * Person 3 hasn't delivered the YOLO API yet. Instead of silently
 * mocking inside the repository (which hides real network failures
 * during a demo), we make the mock an explicit, visible switch here.
 *
 * WHEN PERSON 3 IS READY:
 * 1. Set USE_MOCK_AI = false
 * 2. Replace AI_VERIFY_URL with their real endpoint
 * That's it — no other file needs to change.
 */
object ApiConfig {

    // 🔥 FLIP THIS TO false WHEN PERSON 3'S API IS LIVE
    const val MOCK_VERIFIED_SUCCESS =false
    const val USE_MOCK_AI = true

    // 🔥 REPLACE WITH PERSON 3'S ACTUAL URL WHEN THEY SHARE IT
    // Example once ready: "http://192.168.1.42:5000/verify"
    const val AI_VERIFY_URL = "http://YOUR_PERSON3_IP:5000/verify"

    const val REQUEST_TIMEOUT_MS = 15_000L
    const val MAX_RETRIES = 2

    // Simulated network delay for mock mode, so the UI/loading state
    // behaves the same way it will with a real network call.
    const val MOCK_DELAY_MS = 1200L
}