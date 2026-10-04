package com.yourteam.smartcare.escalation

import android.content.Context
import android.content.Intent
import android.util.Log
import com.yourteam.smartcare.utils.NotificationHelper
import com.yourteam.smartcare.utils.VibrationHelper

/** Runs ONE stage of ONE dose occurrence. Called only by DoseAlarmReceiver. */
object DoseEscalationEngine {
    private const val TAG = "DoseEngine"

    fun onStageAlarm(ctx: Context, occId: String, stage: Int) {
        val occ = DoseStore.get(ctx, occId) ?: return
        val notifications = NotificationHelper(ctx)

        // Source of truth check: a TAKEN/MISSED dose never escalates further.
        if (occ.status != DoseStatus.PENDING) {
            notifications.cancelDose(occId)
            return
        }

        // Idempotent: a stage runs at most once even if the receiver fires twice.
        if (!DoseStore.advanceStage(ctx, occId, stage)) return

        Log.i(TAG, "occ=$occId stage=$stage")

        when (stage) {
            EscalationConfig.STAGE_REMINDER ->
                step1(ctx, occ, notifications)

            EscalationConfig.STAGE_VOICE ->
                step2(ctx, occ, notifications)

            EscalationConfig.STAGE_ALARM ->
                step3(ctx, occ, notifications)

            EscalationConfig.STAGE_TIMEOUT ->
                timeout(ctx, occ, notifications)
        }
    }

    // ==========================================================
    // STEP 1: NORMAL NOTIFICATION
    // T+0
    //
    // Guardian is NOT notified.
    // ==========================================================
    private fun step1(
        ctx: Context,
        occ: DoseOccurrence,
        n: NotificationHelper
    ) {
        n.showDoseStep1(
            occ.id,
            "⏰ Medication Reminder",
            occ.reminderSentence()
        )

        // Roll forward: make sure the following days have
        // their own occurrences.
        DoseStore.loadSchedule(ctx)
            .firstOrNull { it.medicationId == occ.medicationId }
            ?.let {
                EscalationScheduler.ensureUpcomingOccurrences(
                    ctx,
                    it,
                    System.currentTimeMillis()
                )
            }
    }

    // ==========================================================
    // STEP 2: SILENT NOTIFICATION + VIBRATION + TTS
    // T+2 test / T+5 production
    //
    // Silent notification so the notification sound does not
    // collide with the voice reminder.
    // ==========================================================
    private fun step2(
        ctx: Context,
        occ: DoseOccurrence,
        n: NotificationHelper
    ) {
        n.showDoseSilent(
            occ.id,
            "⏰ Medication Reminder",
            occ.reminderSentence(),
            urgent = false
        )

        VibrationHelper(ctx).vibratePattern(
            longArrayOf(0, 500, 200, 500)
        )

        try {
            DoseAudioService.start(
                ctx,
                DoseAudioService.ACTION_SPEAK,
                occ.id
            )
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Could not start voice service " +
                        "(notification + vibration still shown): ${e.message}"
            )
        }
    }

    // ==========================================================
    // STEP 3: URGENT NOTIFICATION + ALARM + VIBRATION + POPUP
    // T+4 test / T+10 production
    //
    // IMPORTANT:
    // This is medication escalation only.
    //
    // It does NOT:
    // - use GPS
    // - notify the guardian
    // - start the SOS service
    // - open EmergencyPopupActivity
    //
    // The popup here is DosePopupActivity, which is completely
    // separate from the existing emergency/SOS popup.
    // ==========================================================
    private fun step3(
        ctx: Context,
        occ: DoseOccurrence,
        n: NotificationHelper
    ) {
        // Urgent medication notification.
        n.showDoseSilent(
            occ.id,
            "🚨 Medication Reminder",
            occ.finalSentence(),
            urgent = true
        )

        // Start the medication alarm audio.
        try {
            DoseAudioService.start(
                ctx,
                DoseAudioService.ACTION_ALARM,
                occ.id
            )
        } catch (e: Exception) {
            // If the foreground audio service cannot start,
            // fall back to an insistent alarm notification + vibration.
            Log.w(
                TAG,
                "Alarm service refused, using notification fallback: ${e.message}"
            )

            n.showDoseAlarmFallback(
                occ.id,
                "🚨 Medication Reminder",
                occ.finalSentence()
            )

            VibrationHelper(ctx).vibratePattern(
                longArrayOf(0, 1000, 500, 1000, 500),
                0
            )
        }

        // ------------------------------------------------------
        // Medication-specific popup.
        // Completely separate from SOS/emergency popup.
        // ------------------------------------------------------
        val popupIntent = Intent(
            ctx,
            DosePopupActivity::class.java
        ).apply {
            putExtra(
                DosePopupActivity.EXTRA_MEDICATION_ID,
                occ.medicationId
            )

            putExtra(
                DosePopupActivity.EXTRA_MED_NAME,
                occ.medName
            )

            putExtra(
                DosePopupActivity.EXTRA_DOSAGE,
                occ.dosage
            )

            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        try {
            ctx.startActivity(popupIntent)
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Could not show medication popup: ${e.message}"
            )
        }
    }

    // ==========================================================
    // FINAL STEP: MISSED
    // T+6 test / T+15 production
    //
    // Stop alarm -> PENDING to MISSED exactly once ->
    // enqueue backend missed-dose sync.
    //
    // The backend/Edge Function handles the guardian
    // notification exactly once.
    // ==========================================================
    private fun timeout(
        ctx: Context,
        occ: DoseOccurrence,
        n: NotificationHelper
    ) {
        if (!DoseStore.transition(
                ctx,
                occ.id,
                setOf(DoseStatus.PENDING),
                DoseStatus.MISSED
            )
        ) {
            return
        }

        // Stop medication alarm audio.
        DoseAudioService.stop(
            ctx,
            occ.id
        )

        // Stop medication vibration.
        VibrationHelper(ctx).cancel()

        // Remove the medication notification.
        n.cancelDose(occ.id)

        // Queue missed-dose synchronization.
        // WorkManager retries until the backend receives it.
        // The server-side claim_missed_dose function guarantees
        // the missed event/guardian notification is processed once.
        DoseSync.enqueueMissed(
            ctx,
            occ
        )
    }
}


/**
 * The ONE entry point for the dashboard "Taken" action
 * and the medication popup's "TAKEN" button.
 */
object DoseAcknowledger {
    private const val TAG = "DoseAck"

    /**
     * Local-first:
     * works offline and immediately stops everything
     * associated with that medication dose.
     */
    fun acknowledge(
        context: Context,
        medicationId: String
    ): Boolean {
        val ctx = context.applicationContext
        val now = System.currentTimeMillis()

        val occ = DoseStore.findForAck(
            ctx,
            medicationId,
            now
        )

        if (occ == null) {
            // No tracked occurrence.
            //
            // Example:
            // extra dose or schedule has not been synced yet.
            //
            // Still record today's scheduled dose if possible.
            val med = DoseStore.loadSchedule(ctx)
                .firstOrNull {
                    it.medicationId == medicationId
                }
                ?: return false

            val at = EscalationScheduler.scheduledTodayMs(med)
                ?: return false

            DoseSync.enqueueAck(
                ctx,
                medicationId,
                at,
                now
            )

            return true
        }

        // Only PENDING or MISSED doses can be acknowledged.
        if (!DoseStore.transition(
                ctx,
                occ.id,
                setOf(
                    DoseStatus.PENDING,
                    DoseStatus.MISSED
                ),
                DoseStatus.TAKEN
            )
        ) {
            return false
        }

        Log.i(
            TAG,
            "TAKEN occ=${occ.id}"
        )

        // Cancel every future escalation stage for this occurrence.
        EscalationScheduler.cancelOccurrence(
            ctx,
            occ.id
        )

        // Stop TTS/alarm audio.
        DoseAudioService.stop(
            ctx,
            occ.id
        )

        // Stop vibration.
        VibrationHelper(ctx).cancel()

        // Remove medication notification.
        NotificationHelper(ctx).cancelDose(
            occ.id
        )

        // Synchronize TAKEN with backend.
        DoseSync.enqueueAck(
            ctx,
            occ.medicationId,
            occ.scheduledAtMs,
            now
        )

        return true
    }
}