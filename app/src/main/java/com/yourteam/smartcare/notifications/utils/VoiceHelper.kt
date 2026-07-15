package com.yourteam.smartcare.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceHelper(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceHelper"
    }

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingMessage: String? = null  // ← Stores message if TTS isn't ready yet

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.ENGLISH)
            isReady = result == TextToSpeech.SUCCESS
            if (isReady) {
                Log.d(TAG, "TextToSpeech initialized successfully!")
                // If there was a message waiting, speak it now!
                pendingMessage?.let {
                    Log.d(TAG, "Speaking pending message: $it")
                    speak(it)
                    pendingMessage = null
                }
            } else {
                Log.e(TAG, "Failed to set language to English")
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed")
            isReady = false
        }
    }

    /**
     * Speak the given text.
     * If TTS isn't ready yet, it stores the text and speaks it automatically when ready.
     */
    fun speak(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            Log.d(TAG, "Speaking: $text")
        } else {
            // Save it for later when TTS wakes up
            pendingMessage = text
            Log.d(TAG, "TTS not ready. Message queued: $text")
        }
    }

    /**
     * Speak the given text after the current queue finishes
     */
    fun speakWithQueue(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
            Log.d(TAG, "Queued speech: $text")
        } else {
            pendingMessage = text
            Log.d(TAG, "TTS not ready. Message queued (add): $text")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        pendingMessage = null
        Log.d(TAG, "TTS shut down")
    }

    fun isReady(): Boolean = isReady
}