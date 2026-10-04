package com.yourteam.smartcare.escalation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yourteam.smartcare.utils.NotificationHelper
import com.yourteam.smartcare.utils.VibrationHelper
import com.yourteam.smartcare.utils.VoiceHelper

/**
 * The ONE audio owner for medication escalation (foreground type: mediaPlayback, no location).
 * Short-lived: started by an alarm at Step 2 (TTS) / Step 3 (alarm), stops itself when idle.
 * Rules: an alarm outranks voice; TTS is cut when an alarm starts; one MediaPlayer, instance-scoped.
 */
class DoseAudioService : Service() {

    companion object {
        private const val TAG = "DoseAudioService"
        const val ACTION_SPEAK = "com.yourteam.smartcare.action.DOSE_SPEAK"
        const val ACTION_ALARM = "com.yourteam.smartcare.action.DOSE_ALARM"
        const val ACTION_STOP = "com.yourteam.smartcare.action.DOSE_STOP"
        private const val EXTRA_OCC_ID = "occ_id"
        private const val SPEECH_TIMEOUT_MS = 30_000L
        private const val WATCHDOG_MS = 3_000L

        @Volatile var isRunning = false
            private set

        private fun intent(ctx: Context, action: String, occId: String?) =
            Intent(ctx, DoseAudioService::class.java)
                .setAction(action)
                .putExtra(EXTRA_OCC_ID, occId)

        /** Throws if Android refuses; the caller falls back to notification-only behaviour. */
        fun start(ctx: Context, action: String, occId: String) =
            ContextCompat.startForegroundService(
                ctx,
                intent(ctx, action, occId)
            )

        /** Stop audio for one occurrence (null = everything). No-op if the service is not running. */
        fun stop(ctx: Context, occId: String?) {
            if (!isRunning) return

            try {
                ctx.startService(
                    intent(ctx, ACTION_STOP, occId)
                )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "stop failed: ${e.message}"
                )
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var voice: VoiceHelper? = null
    private var vibration: VibrationHelper? = null
    private var focusRequest: AudioFocusRequest? = null

    private val alarmOccs = linkedSetOf<String>()

    private var speechInFlight = 0
    private var speakingOcc: String? = null

    private val speechTimeout = Runnable {
        speechInFlight = 0
        speakingOcc = null
        stopIfIdle()
    }

    /**
     * Defensive: silence anything whose dose is no longer PENDING,
     * or past the timeout instant.
     */
    private val watchdog = object : Runnable {

        override fun run() {

            val now = System.currentTimeMillis()

            alarmOccs.toList().forEach { id ->

                val occ = DoseStore.get(
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

        // Must go foreground immediately on every start.
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIFICATION_ID_DOSE_SERVICE,
            NotificationHelper(this).buildDoseServiceNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        val occId =
            intent?.getStringExtra(EXTRA_OCC_ID)

        Log.i(
            TAG,
            "onStartCommand: action=${intent?.action}, occId=$occId"
        )

        when (intent?.action) {

            ACTION_SPEAK ->
                occId?.let(::handleSpeak)

            ACTION_ALARM ->
                occId?.let(::handleAlarm)

            ACTION_STOP ->
                handleStop(occId)
        }

        stopIfIdle()

        return START_NOT_STICKY
    }

    // ---------------- Step 2: voice ----------------

    private fun handleSpeak(
        occId: String
    ) {

        val occ = DoseStore.get(
            this,
            occId
        )

        if (
            occ == null ||
            occ.status != DoseStatus.PENDING
        ) {
            return
        }

        if (alarmOccs.isNotEmpty()) {
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

        v.speak(
            occ.reminderSentence()
        ) {

            handler.post {

                speechInFlight =
                    (speechInFlight - 1)
                        .coerceAtLeast(0)

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

    // ---------------- Step 3: alarm ----------------

    private fun handleAlarm(
        occId: String
    ) {

        Log.i(
            TAG,
            "handleAlarm: occId=$occId"
        )

        val occ = DoseStore.get(
            this,
            occId
        )

        if (
            occ == null ||
            occ.status != DoseStatus.PENDING
        ) {
            return
        }

        alarmOccs.add(
            occId
        )

        voice?.stop()

        speechInFlight = 0
        speakingOcc = null

        startPlayerIfNeeded()

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

        handler.removeCallbacks(
            watchdog
        )

        handler.postDelayed(
            watchdog,
            WATCHDOG_MS
        )
    }

    private fun startPlayerIfNeeded() {

        if (player != null) {
            return
        }

        Log.i(
            TAG,
            "startPlayerIfNeeded: creating MediaPlayer"
        )

        requestFocus()

        try {

            val uri =
                RingtoneManager.getActualDefaultRingtoneUri(
                    this,
                    RingtoneManager.TYPE_ALARM
                )
                    ?: Settings.System.DEFAULT_ALARM_ALERT_URI

            Log.i(
                TAG,
                "startPlayerIfNeeded: alarmUri=$uri"
            )

            player =
                MediaPlayer().apply {

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

                    setDataSource(
                        applicationContext,
                        uri
                    )

                    isLooping = true

                    setOnErrorListener { _, _, _ ->

                        handler.post {
                            releasePlayer()
                        }

                        true
                    }

                    prepare()

                    start()

                    Log.i(
                        TAG,
                        "startPlayerIfNeeded: MediaPlayer.start() succeeded"
                    )
                }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Alarm playback failed",
                e
            )

            releasePlayer()
        }
    }

    // ---------------- Stop / cleanup ----------------

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
            "handleStop: occId=$occId, remainingAlarms=$alarmOccs"
        )

        if (
            occId == null ||
            occId == speakingOcc
        ) {

            voice?.stop()

            speechInFlight = 0
            speakingOcc = null
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

        if (player != null) {

            Log.i(
                TAG,
                "releasePlayer: stopping and releasing MediaPlayer"
            )
        }

        player?.let {

            runCatching {
                it.stop()
            }

            runCatching {
                it.release()
            }
        }

        player = null
    }

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

        am.requestAudioFocus(
            focusRequest!!
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

            (
                    getSystemService(
                        Context.AUDIO_SERVICE
                    ) as AudioManager
                    ).abandonAudioFocusRequest(
                    it
                )
        }

        focusRequest = null
    }

    override fun onDestroy() {

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