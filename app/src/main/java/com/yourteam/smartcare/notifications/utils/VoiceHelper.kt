package com.yourteam.smartcare.utils

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Backward compatible with existing SmartCare callers.
 *
 * Existing usage:
 *     VoiceHelper(context).speak("text")
 *
 * New medication escalation usage:
 *     VoiceHelper(context, alarmStream = true)
 *
 * The alarmStream option makes TTS use the alarm audio stream.
 */
class VoiceHelper(
    context: Context,
    private val alarmStream: Boolean = false
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceHelper"
    }

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var initFailed = false

    private var pendingMessage: String? = null
    private var pendingMode = TextToSpeech.QUEUE_FLUSH
    private var pendingCallback: (() -> Unit)? = null

    private val callbacks =
        ConcurrentHashMap<String, () -> Unit>()

    init {
        tts = TextToSpeech(
            context.applicationContext,
            this
        )
    }

    private val progress =
        object : UtteranceProgressListener() {

            override fun onStart(utteranceId: String?) {
                // Nothing required here.
            }

            override fun onDone(utteranceId: String?) {
                finish(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finish(utteranceId)
            }

            override fun onError(
                utteranceId: String?,
                errorCode: Int
            ) {
                finish(utteranceId)
            }

            override fun onStop(
                utteranceId: String?,
                interrupted: Boolean
            ) {
                finish(utteranceId)
            }
        }

    private fun finish(utteranceId: String?) {
        utteranceId?.let {
            callbacks.remove(it)?.invoke()
        }
    }

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            val result =
                tts?.setLanguage(Locale.ENGLISH)

            isReady =
                result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED

            if (isReady) {

                /*
                 * Only medication escalation creates VoiceHelper
                 * with alarmStream = true.
                 *
                 * Existing SOS callers continue using the
                 * normal audio stream.
                 */
                if (alarmStream) {
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(
                                AudioAttributes.USAGE_ALARM
                            )
                            .setContentType(
                                AudioAttributes.CONTENT_TYPE_SPEECH
                            )
                            .build()
                    )
                }

                tts?.setOnUtteranceProgressListener(
                    progress
                )

                Log.d(
                    TAG,
                    "TextToSpeech initialized successfully!"
                )

                /*
                 * If speak() was called before TTS finished
                 * initializing, speak the pending message now.
                 */
                pendingMessage?.let {

                    val callback = pendingCallback

                    pendingMessage = null
                    pendingCallback = null

                    speakInternal(
                        it,
                        pendingMode,
                        callback
                    )
                }

            } else {

                Log.e(
                    TAG,
                    "Failed to set language to English"
                )

                failInit()
            }

        } else {

            Log.e(
                TAG,
                "TextToSpeech initialization failed"
            )

            failInit()
        }
    }

    private fun failInit() {

        isReady = false
        initFailed = true

        pendingMessage = null

        pendingCallback?.invoke()
        pendingCallback = null
    }

    /**
     * Speaks immediately, replacing anything currently queued.
     *
     * Existing SmartCare callers can continue using:
     *
     *     voiceHelper.speak("Hello")
     */
    fun speak(
        text: String,
        onDone: (() -> Unit)? = null
    ) {
        speakInternal(
            text,
            TextToSpeech.QUEUE_FLUSH,
            onDone
        )
    }

    /**
     * Adds speech after the current speech queue.
     */
    fun speakWithQueue(
        text: String,
        onDone: (() -> Unit)? = null
    ) {
        speakInternal(
            text,
            TextToSpeech.QUEUE_ADD,
            onDone
        )
    }

    private fun speakInternal(
        text: String,
        mode: Int,
        onDone: (() -> Unit)?
    ) {

        when {

            // TTS is ready
            isReady -> {

                val utteranceId =
                    UUID.randomUUID().toString()

                onDone?.let {
                    callbacks[utteranceId] = it
                }

                val result =
                    tts?.speak(
                        text,
                        mode,
                        null,
                        utteranceId
                    )

                if (result != TextToSpeech.SUCCESS) {
                    callbacks
                        .remove(utteranceId)
                        ?.invoke()
                }

                Log.d(
                    TAG,
                    "Speaking: $text"
                )
            }

            // TTS initialization failed
            initFailed -> {
                onDone?.invoke()
            }

            // TTS is still initializing
            else -> {

                pendingMessage = text
                pendingMode = mode
                pendingCallback = onDone

                Log.d(
                    TAG,
                    "TTS not ready. Message queued: $text"
                )
            }
        }
    }

    /**
     * Stops the current speech immediately.
     *
     * Used when:
     * - user taps Taken
     * - medication escalation moves from T+5 voice
     *   to T+10 alarm
     * - escalation is cancelled
     */
    fun stop() {

        pendingMessage = null
        pendingCallback = null

        tts?.stop()
    }

    /**
     * Completely releases the TextToSpeech engine.
     */
    fun shutdown() {

        tts?.stop()
        tts?.shutdown()

        tts = null
        isReady = false

        pendingMessage = null
        pendingCallback = null

        callbacks.clear()

        Log.d(
            TAG,
            "TTS shut down"
        )
    }

    fun isReady(): Boolean = isReady
}