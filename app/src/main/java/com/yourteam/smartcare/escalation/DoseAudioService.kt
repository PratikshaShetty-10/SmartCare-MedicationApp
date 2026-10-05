package com.yourteam.smartcare.escalation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yourteam.smartcare.R
import com.yourteam.smartcare.utils.NotificationHelper
import com.yourteam.smartcare.utils.VibrationHelper
import com.yourteam.smartcare.utils.VoiceHelper

/**
 * ONE audio owner for medication escalation.
 *
 * Step 2:
 * - TTS voice reminder
 *
 * Step 3:
 * - medication_alarm.mp3
 * - looping alarm
 * - vibration
 *
 * The alarm uses the application's bundled raw resource instead of
 * the device's system ringtone/alarm URI.
 */
class DoseAudioService : Service() {

    companion object {
        private const val TAG = "DoseAudioService"

        const val ACTION_SPEAK =
            "com.yourteam.smartcare.action.DOSE_SPEAK"

        const val ACTION_ALARM =
            "com.yourteam.smartcare.action.DOSE_ALARM"

        const val ACTION_STOP =
            "com.yourteam.smartcare.action.DOSE_STOP"

        private const val EXTRA_OCC_ID = "occ_id"

        private const val SPEECH_TIMEOUT_MS = 30_000L
        private const val WATCHDOG_MS = 3_000L

        @Volatile
        var isRunning = false
            private set

        private fun intent(
            ctx: Context,
            action: String,
            occId: String?
        ): Intent {
            return Intent(
                ctx,
                DoseAudioService::class.java
            )
                .setAction(action)
                .putExtra(EXTRA_OCC_ID, occId)
        }

        /**
         * Starts the foreground audio service.
         */
        fun start(
            ctx: Context,
            action: String,
            occId: String
        ) {
            ContextCompat.startForegroundService(
                ctx,
                intent(
                    ctx,
                    action,
                    occId
                )
            )
        }

        /**
         * Stops audio for one occurrence.
         *
         * null = stop everything.
         */
        fun stop(
            ctx: Context,
            occId: String?
        ) {
            if (!isRunning) return

            try {
                ctx.startService(
                    intent(
                        ctx,
                        ACTION_STOP,
                        occId
                    )
                )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "stop failed: ${e.message}"
                )
            }
        }
    }

    private val handler =
        Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null

    private var voice: VoiceHelper? = null

    private var vibration: VibrationHelper? = null

    private var focusRequest: AudioFocusRequest? = null

    /**
     * Occurrences currently playing the alarm.
     */
    private val alarmOccs =
        linkedSetOf<String>()

    private var speechInFlight = 0

    private var speakingOcc: String? = null

    /**
     * Prevents TTS from remaining active forever if the
     * VoiceHelper callback is not returned.
     */
    private val speechTimeout =
        Runnable {

            Log.w(
                TAG,
                "speech timeout reached"
            )

            speechInFlight = 0
            speakingOcc = null

            stopIfIdle()
        }

    /**
     * Defensive watchdog for the looping medication alarm.
     *
     * It checks the DoseStore and stops alarms whose dose
     * is no longer pending or whose escalation window expired.
     */
    private val watchdog =
        object : Runnable {

            override fun run() {

                val now =
                    System.currentTimeMillis()

                alarmOccs
                    .toList()
                    .forEach { id ->

                        val occ =
                            DoseStore.get(
                                this@DoseAudioService,
                                id
                            )

                        val expired =
                            occ == null ||
                                    occ.status != DoseStatus.PENDING ||
                                    now >
                                    occ.scheduledAtMs +
                                    EscalationConfig.offsetMs(
                                        EscalationConfig.STAGE_TIMEOUT
                                    ) +
                                    EscalationConfig.ALARM_SAFETY_MARGIN_MS

                        if (expired) {

                            Log.i(
                                TAG,
                                "watchdog: stopping expired alarm " +
                                        "occ=$id " +
                                        "now=$now " +
                                        "scheduled=${occ?.scheduledAtMs}"
                            )

                            alarmOccs.remove(id)
                        }
                    }

                if (alarmOccs.isEmpty()) {

                    stopPlayback()
                    stopIfIdle()

                } else {

                    handler.postDelayed(
                        this,
                        WATCHDOG_MS
                    )
                }
            }
        }

    override fun onCreate() {

        super.onCreate()

        isRunning = true

        Log.i(
            TAG,
            "onCreate: audio service started"
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        /**
         * Android requires the foreground service notification
         * to be started immediately.
         */
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID_DOSE_SERVICE,
            NotificationHelper(this)
                .buildDoseServiceNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        val occId =
            intent?.getStringExtra(
                EXTRA_OCC_ID
            )

        Log.i(
            TAG,
            "onStartCommand: " +
                    "action=${intent?.action}, " +
                    "occId=$occId"
        )

        when (intent?.action) {

            ACTION_SPEAK -> {
                occId?.let(
                    ::handleSpeak
                )
            }

            ACTION_ALARM -> {
                occId?.let(
                    ::handleAlarm
                )
            }

            ACTION_STOP -> {
                handleStop(
                    occId
                )
            }
        }

        stopIfIdle()

        return START_NOT_STICKY
    }

    // ==========================================================
    // STEP 2 — VOICE / TTS
    // ==========================================================

    private fun handleSpeak(
        occId: String
    ) {

        val occ =
            DoseStore.get(
                this,
                occId
            )

        if (
            occ == null ||
            occ.status != DoseStatus.PENDING
        ) {

            Log.i(
                TAG,
                "handleSpeak: occurrence not pending: $occId"
            )

            return
        }

        /**
         * If an alarm is already active, alarm has priority.
         */
        if (alarmOccs.isNotEmpty()) {

            Log.i(
                TAG,
                "handleSpeak: alarm already active; skipping TTS"
            )

            return
        }

        requestFocus()

        speechInFlight++

        speakingOcc = occId

        handler.removeCallbacks(
            speechTimeout
        )

        handler.postDelayed(
            speechTimeout,
            SPEECH_TIMEOUT_MS
        )

        val v =
            voice
                ?: VoiceHelper(
                    applicationContext,
                    alarmStream = true
                ).also {
                    voice = it
                }

        Log.i(
            TAG,
            "handleSpeak: speaking ${occ.reminderSentence()}"
        )

        v.speak(
            occ.reminderSentence()
        ) {

            handler.post {

                speechInFlight =
                    (
                            speechInFlight - 1
                            ).coerceAtLeast(0)

                if (speechInFlight == 0) {

                    speakingOcc = null

                    handler.removeCallbacks(
                        speechTimeout
                    )
                }

                stopIfIdle()
            }
        }
    }

    // ==========================================================
    // STEP 3 — ALARM
    // ==========================================================

    private fun handleAlarm(
        occId: String
    ) {

        Log.i(
            TAG,
            "handleAlarm: occId=$occId"
        )

        val occ =
            DoseStore.get(
                this,
                occId
            )

        if (
            occ == null ||
            occ.status != DoseStatus.PENDING
        ) {

            Log.i(
                TAG,
                "handleAlarm: occurrence is not pending"
            )

            return
        }

        /**
         * Register this occurrence as actively alarming.
         */
        alarmOccs.add(
            occId
        )

        /**
         * Alarm has priority over TTS.
         */
        voice?.stop()

        speechInFlight = 0
        speakingOcc = null

        handler.removeCallbacks(
            speechTimeout
        )

        /**
         * Start the bundled medication alarm.
         */
        startPlayerIfNeeded()

        /**
         * Start repeating vibration.
         */
        if (vibration == null) {

            vibration =
                VibrationHelper(this).also {

                    it.vibratePattern(
                        longArrayOf(
                            0,
                            1000,
                            500,
                            1000,
                            500
                        ),
                        0
                    )
                }
        }

        /**
         * Start watchdog.
         */
        handler.removeCallbacks(
            watchdog
        )

        handler.postDelayed(
            watchdog,
            WATCHDOG_MS
        )
    }

    /**
     * Plays the bundled:
     *
     * app/src/main/res/raw/medication_alarm.mp3
     *
     * IMPORTANT:
     * We deliberately do NOT use RingtoneManager here.
     *
     * That avoids Android's MediaStore permission problem:
     *
     * "has no access to content://media/..."
     */
    private fun startPlayerIfNeeded() {

        if (player != null) {

            Log.i(
                TAG,
                "startPlayerIfNeeded: player already running"
            )

            return
        }

        Log.i(
            TAG,
            "startPlayerIfNeeded: creating bundled alarm MediaPlayer"
        )

        requestFocus()

        try {

            /**
             * MediaPlayer.create() loads the resource
             * directly from the application package.
             *
             * No READ_MEDIA_AUDIO permission is required.
             * No MediaStore URI is used.
             */
            player =
                MediaPlayer.create(
                    this,
                    R.raw.medication_alarm
                )

            if (player == null) {

                throw IllegalStateException(
                    "MediaPlayer.create() returned null"
                )
            }

            player?.apply {

                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            AudioAttributes.USAGE_ALARM
                        )
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build()
                )

                /**
                 * Repeat the 2-minute alarm file until:
                 *
                 * - user takes medicine
                 * - timeout/missed stage occurs
                 * - watchdog expires it
                 */
                isLooping = true

                setOnErrorListener { _, what, extra ->

                    Log.e(
                        TAG,
                        "MediaPlayer error: " +
                                "what=$what extra=$extra"
                    )

                    handler.post {
                        releasePlayer()
                    }

                    true
                }

                start()
            }

            Log.i(
                TAG,
                "startPlayerIfNeeded: " +
                        "bundled medication_alarm.mp3 started successfully"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Alarm playback failed",
                e
            )

            releasePlayer()
        }
    }

    // ==========================================================
    // STOP / CLEANUP
    // ==========================================================

    private fun handleStop(
        occId: String?
    ) {

        if (occId == null) {

            alarmOccs.clear()

        } else {

            alarmOccs.remove(
                occId
            )
        }

        Log.i(
            TAG,
            "handleStop: " +
                    "occId=$occId, " +
                    "remainingAlarms=$alarmOccs"
        )

        if (
            occId == null ||
            occId == speakingOcc
        ) {

            voice?.stop()

            speechInFlight = 0

            speakingOcc = null

            handler.removeCallbacks(
                speechTimeout
            )
        }

        if (alarmOccs.isEmpty()) {

            stopPlayback()
        }
    }

    private fun stopIfIdle() {

        if (
            alarmOccs.isEmpty() &&
            speechInFlight == 0
        ) {

            stopPlayback()

            ServiceCompat.stopForeground(
                this,
                ServiceCompat.STOP_FOREGROUND_REMOVE
            )

            stopSelf()
        }
    }

    private fun stopPlayback() {

        releasePlayer()

        vibration?.cancel()

        vibration = null

        handler.removeCallbacks(
            watchdog
        )

        abandonFocus()
    }

    private fun releasePlayer() {

        player?.let {

            Log.i(
                TAG,
                "releasePlayer: stopping and releasing MediaPlayer"
            )

            runCatching {
                if (it.isPlaying) {
                    it.stop()
                }
            }

            runCatching {
                it.release()
            }
        }

        player = null
    }

    // ==========================================================
    // AUDIO FOCUS
    // ==========================================================

    private fun requestFocus() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O ||
            focusRequest != null
        ) {
            return
        }

        val am =
            getSystemService(
                Context.AUDIO_SERVICE
            ) as AudioManager

        focusRequest =
            AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            AudioAttributes.USAGE_ALARM
                        )
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()

        val result =
            am.requestAudioFocus(
                focusRequest!!
            )

        Log.i(
            TAG,
            "requestAudioFocus: result=$result"
        )
    }

    private fun abandonFocus() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        focusRequest?.let {

            val am =
                getSystemService(
                    Context.AUDIO_SERVICE
                ) as AudioManager

            am.abandonAudioFocusRequest(
                it
            )
        }

        focusRequest = null
    }

    override fun onDestroy() {

        Log.i(
            TAG,
            "onDestroy: audio service stopping"
        )

        isRunning = false

        handler.removeCallbacksAndMessages(
            null
        )

        stopPlayback()

        voice?.shutdown()

        voice = null

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
