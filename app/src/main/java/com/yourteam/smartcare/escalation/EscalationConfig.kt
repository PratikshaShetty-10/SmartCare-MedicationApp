package com.yourteam.smartcare.escalation

/**
 * ONE place for all medication-escalation timing.
 *
 *   TESTING     (STEP_MINUTES = 2): Step1 T+0, Step2 T+2, Step3 T+4, MISSED T+6
 *   PRODUCTION  (STEP_MINUTES = 5): Step1 T+0, Step2 T+5, Step3 T+10, MISSED T+15
 *
 * To go to production change ONLY the number below from 2 to 5.
 */
object EscalationConfig {

    // TESTING: 2 minutes   |   PRODUCTION: 5 minutes
    private const val STEP_MINUTES = 2L

    const val STEP_INTERVAL_MS = STEP_MINUTES * 60 * 1000L

    const val STAGE_REMINDER = 1   // normal notification + beep
    const val STAGE_VOICE = 2      // notification + vibration + TTS
    const val STAGE_ALARM = 3      // urgent notification + alarm sound + vibration
    const val STAGE_TIMEOUT = 4    // stop alarm, mark MISSED, notify guardian

    /** Milliseconds after the scheduled time at which a stage fires. */
    fun offsetMs(stage: Int): Long = (stage - 1) * STEP_INTERVAL_MS

    /** Pressing "Take" up to this long BEFORE the scheduled time counts for that dose. */
    const val EARLY_ACK_WINDOW_MS = 2 * 60 * 60 * 1000L

    /** Pressing "Take" this long AFTER a dose was marked MISSED upgrades it to late-TAKEN. */
    const val LATE_ACK_WINDOW_MS = 12 * 60 * 60 * 1000L

    /** Safety margin: the audio service silences itself this long after the timeout instant. */
    const val ALARM_SAFETY_MARGIN_MS = 5_000L
}
