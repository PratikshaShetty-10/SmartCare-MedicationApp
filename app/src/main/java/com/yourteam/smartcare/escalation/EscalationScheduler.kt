package com.yourteam.smartcare.escalation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.yourteam.smartcare.MainActivity
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.data.model.Medication
import com.yourteam.smartcare.data.model.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Registers one exact alarm per STAGE of each dose occurrence (4 alarms per dose).
 * AlarmManager is the clock: it survives process death and Doze. Nothing uses delay().
 */
object EscalationScheduler {

    private const val TAG = "EscalationScheduler"

    const val ACTION_STAGE = "com.yourteam.smartcare.action.DOSE_STAGE"
    const val EXTRA_OCC_ID = "occ_id"
    const val EXTRA_STAGE = "stage"

    /** Pulls THIS elder's medications from Supabase and (re)builds local alarms. Safe to call often. */
    suspend fun syncFromServer(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext

        Log.i(TAG, "syncFromServer() START")

        try {
            val user =
                SupabaseClient.client.auth.currentUserOrNull()
                    ?: run {
                        Log.e(
                            TAG,
                            "syncFromServer(): NO AUTHENTICATED USER"
                        )
                        return@withContext
                    }

            Log.i(
                TAG,
                "syncFromServer(): authenticated user found"
            )

            // Only medications whose elder_id is the logged-in user:
            // guardian phones schedule nothing.
            val meds =
                SupabaseClient.client
                    .from("medications")
                    .select {
                        filter {
                            eq("elder_id", user.id)
                        }
                    }
                    .decodeList<Medication>()

            Log.i(
                TAG,
                "syncFromServer(): medications fetched=${meds.size}"
            )

            meds.forEach { med ->
                Log.i(
                    TAG,
                    "Medication: id=${med.id}, " +
                            "name=${med.medName}, " +
                            "time=${med.scheduledTime}"
                )
            }

            runCatching {
                SupabaseClient.client
                    .from("profiles")
                    .select {
                        filter {
                            eq("id", user.id)
                        }
                    }
                    .decodeSingleOrNull<Profile>()
                    ?.name
            }.getOrNull()?.let {
                DoseStore.saveElderName(app, it)
            }

            applySchedule(
                app,
                meds.mapNotNull { m ->
                    m.id?.let {
                        ScheduledMed(
                            it,
                            m.medName,
                            m.dosage,
                            m.scheduledTime
                        )
                    }
                }
            )

            Log.i(
                TAG,
                "syncFromServer() COMPLETE"
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Schedule sync failed, keeping existing alarms: ${e.message}",
                e
            )
        }
    }

    fun applySchedule(
        ctx: Context,
        schedule: List<ScheduledMed>
    ) {

        Log.i(
            TAG,
            "applySchedule(): received ${schedule.size} medications"
        )

        DoseStore.saveSchedule(ctx, schedule)

        val now = System.currentTimeMillis()

        DoseStore.prune(ctx, now)

        reconcile(
            ctx,
            schedule
        )

        schedule.forEach {
            ensureUpcomingOccurrences(
                ctx,
                it,
                now
            )
        }

        recoverAfterRestart(ctx)

        Log.i(
            TAG,
            "applySchedule(): COMPLETE"
        )
    }

    /** Removes not-yet-started occurrences whose medication was deleted/edited. */
    private fun reconcile(
        ctx: Context,
        schedule: List<ScheduledMed>
    ) {

        val byId = schedule.associateBy {
            it.medicationId
        }

        DoseStore.all(ctx)
            .filter {
                it.status == DoseStatus.PENDING &&
                        it.stage == 0
            }
            .forEach { occ ->

                val med = byId[occ.medicationId]

                val timeOfDay =
                    zoned(occ.scheduledAtMs).toLocalTime()

                val stale =
                    med == null ||
                            parseTime(med.time) != timeOfDay ||
                            med.medName != occ.medName ||
                            med.dosage != occ.dosage

                if (stale) {

                    Log.i(
                        TAG,
                        "Removing stale occurrence: ${occ.id}"
                    )

                    cancelOccurrence(
                        ctx,
                        occ.id
                    )

                    DoseStore.delete(
                        ctx,
                        occ.id
                    )
                }
            }
    }

    private fun zoned(ms: Long) =
        java.time.Instant
            .ofEpochMilli(ms)
            .atZone(ZoneId.systemDefault())

    /** Creates today's (if still in the future) and tomorrow's occurrence. Each is independent. */
    fun ensureUpcomingOccurrences(
        ctx: Context,
        med: ScheduledMed,
        nowMs: Long
    ) {

        val time = parseTime(med.time)
            ?: run {
                Log.e(
                    TAG,
                    "Invalid medication time: '${med.time}' " +
                            "for ${med.medName}"
                )
                return
            }

        Log.i(
            TAG,
            "ensureUpcomingOccurrences(): " +
                    "med=${med.medName}, " +
                    "time=${med.time}, " +
                    "now=${DoseFormat.timeLabel(nowMs)}"
        )

        val zone = ZoneId.systemDefault()

        val today = LocalDate.now(zone)

        for (d in 0L..1L) {

            val at =
                ZonedDateTime
                    .of(
                        today.plusDays(d),
                        time,
                        zone
                    )
                    .toInstant()
                    .toEpochMilli()

            Log.i(
                TAG,
                "Checking occurrence: " +
                        "med=${med.medName}, " +
                        "date=${today.plusDays(d)}, " +
                        "scheduled=${DoseFormat.timeLabel(at)}, " +
                        "scheduledAtMs=$at, " +
                        "nowMs=$nowMs"
            )

            if (at <= nowMs) {

                Log.i(
                    TAG,
                    "Skipping past occurrence: " +
                            "${med.medName} @ ${DoseFormat.timeLabel(at)}"
                )

                continue
            }

            val occ =
                DoseOccurrence(
                    id = DoseStore.occurrenceId(
                        med.medicationId,
                        at
                    ),
                    medicationId = med.medicationId,
                    medName = med.medName,
                    dosage = med.dosage,
                    elderName = DoseStore.loadElderName(ctx),
                    scheduledAtMs = at
                )

            val created =
                DoseStore.putIfAbsent(
                    ctx,
                    occ
                )

            Log.i(
                TAG,
                "Occurrence " +
                        if (created) "CREATED" else "ALREADY_EXISTS" +
                                ": id=${occ.id}, " +
                                "scheduled=${DoseFormat.timeLabel(at)}, " +
                                "stage=${occ.stage}, " +
                                "status=${occ.status}"
            )

            if (created) {

                for (
                stage in
                EscalationConfig.STAGE_REMINDER..
                        EscalationConfig.STAGE_TIMEOUT
                ) {

                    scheduleStage(
                        ctx,
                        occ,
                        stage,
                        at + EscalationConfig.offsetMs(stage)
                    )
                }

                Log.i(
                    TAG,
                    "Scheduled ${occ.medName} @ " +
                            "${DoseFormat.timeLabel(at)} " +
                            "(${occ.id})"
                )
            }
        }
    }

    /**
     * After reboot / app update / exact-alarm permission change, AlarmManager forgets alarms.
     * Re-register every unfinished stage. If several stages are already overdue, only the LATEST
     * overdue one fires (1s from now) so the elder is not hit with a replay of old steps.
     * Never starts a foreground service directly (Android 15 forbids that from BOOT_COMPLETED).
     */
    fun recoverAfterRestart(ctx: Context) {

        val now = System.currentTimeMillis()

        Log.i(
            TAG,
            "recoverAfterRestart(): checking pending occurrences"
        )

        DoseStore.all(ctx)
            .filter {
                it.status == DoseStatus.PENDING
            }
            .forEach { occ ->

                Log.i(
                    TAG,
                    "Recovering occurrence: " +
                            "id=${occ.id}, " +
                            "stage=${occ.stage}, " +
                            "status=${occ.status}"
                )

                val remaining =
                    (
                            occ.stage + 1..
                                    EscalationConfig.STAGE_TIMEOUT
                            ).toList()

                val overdue =
                    remaining.filter {
                        occ.scheduledAtMs +
                                EscalationConfig.offsetMs(it) <= now
                    }

                val collapse =
                    overdue.maxOrNull()

                remaining.forEach { s ->

                    val t =
                        occ.scheduledAtMs +
                                EscalationConfig.offsetMs(s)

                    when {

                        s == collapse -> {

                            Log.i(
                                TAG,
                                "Recovering overdue stage=$s " +
                                        "for ${occ.id} at now+1s"
                            )

                            scheduleStage(
                                ctx,
                                occ,
                                s,
                                now + 1_000
                            )
                        }

                        t > now -> {

                            Log.i(
                                TAG,
                                "Recovering future stage=$s " +
                                        "for ${occ.id} at " +
                                        "${DoseFormat.timeLabel(t)}"
                            )

                            scheduleStage(
                                ctx,
                                occ,
                                s,
                                t
                            )
                        }

                        else -> Unit
                    }
                }
            }

        Log.i(
            TAG,
            "recoverAfterRestart(): COMPLETE"
        )
    }

    fun cancelOccurrence(
        ctx: Context,
        occId: String
    ) {

        Log.i(
            TAG,
            "Cancelling alarms for occurrence=$occId"
        )

        val am =
            ctx.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

        for (
        stage in
        EscalationConfig.STAGE_REMINDER..
                EscalationConfig.STAGE_TIMEOUT
        ) {

            stagePendingIntent(
                ctx,
                occId,
                stage,
                PendingIntent.FLAG_NO_CREATE
            )?.let {

                am.cancel(it)

                it.cancel()
            }
        }
    }

    private fun scheduleStage(
        ctx: Context,
        occ: DoseOccurrence,
        stage: Int,
        triggerAtMs: Long
    ) {

        val am =
            ctx.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

        // Diagnostic:
        // This tells us what Android reports about exact-alarm access.
        val canExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    am.canScheduleExactAlarms()

        Log.i(
            TAG,
            "Exact alarm check: " +
                    "sdk=${Build.VERSION.SDK_INT}, " +
                    "canScheduleExactAlarms=$canExact"
        )

        val pi =
            stagePendingIntent(
                ctx,
                occ.id,
                stage,
                PendingIntent.FLAG_UPDATE_CURRENT
            ) ?: run {
                Log.e(
                    TAG,
                    "Could not create PendingIntent: " +
                            "stage=$stage, occ=${occ.id}"
                )
                return
            }

        Log.i(
            TAG,
            "Scheduling alarm: " +
                    "stage=$stage, " +
                    "occ=${occ.id}, " +
                    "trigger=${DoseFormat.timeLabel(triggerAtMs)}, " +
                    "triggerAtMs=$triggerAtMs"
        )

        try {

            if (canExact) {

                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    pi
                )

                Log.i(
                    TAG,
                    "Alarm registered successfully: " +
                            "stage=$stage, occ=${occ.id}"
                )

            } else {

                // Keep the existing fallback for this diagnostic run.
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(
                        triggerAtMs,
                        showAppIntent(ctx)
                    ),
                    pi
                )

                Log.i(
                    TAG,
                    "AlarmClock fallback registered: " +
                            "stage=$stage, occ=${occ.id}"
                )
            }

        } catch (e: SecurityException) {

            Log.e(
                TAG,
                "Exact alarm registration failed: " +
                        "stage=$stage, " +
                        "occ=${occ.id}, " +
                        "triggerAtMs=$triggerAtMs",
                e
            )

            try {

                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(
                        triggerAtMs,
                        showAppIntent(ctx)
                    ),
                    pi
                )

                Log.i(
                    TAG,
                    "AlarmClock fallback registered after SecurityException: " +
                            "stage=$stage, occ=${occ.id}"
                )

            } catch (fallbackError: SecurityException) {

                Log.e(
                    TAG,
                    "AlarmClock fallback also failed: " +
                            "stage=$stage, " +
                            "occ=${occ.id}",
                    fallbackError
                )
            }
        }
    }

    /** Unique per (occurrence, stage) via the data Uri, so alarms never replace each other. */
    private fun stagePendingIntent(
        ctx: Context,
        occId: String,
        stage: Int,
        flag: Int
    ): PendingIntent? {

        val intent =
            Intent(
                ctx,
                DoseAlarmReceiver::class.java
            ).apply {

                action = ACTION_STAGE

                data =
                    Uri.parse(
                        "smartcare://dose/$occId/$stage"
                    )

                putExtra(
                    EXTRA_OCC_ID,
                    occId
                )

                putExtra(
                    EXTRA_STAGE,
                    stage
                )
            }

        return PendingIntent.getBroadcast(
            ctx,
            0,
            intent,
            flag or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showAppIntent(
        ctx: Context
    ): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            0,
            Intent(
                ctx,
                MainActivity::class.java
            ),
            PendingIntent.FLAG_IMMUTABLE
        )

    fun parseTime(
        raw: String
    ): LocalTime? =
        runCatching {
            LocalTime
                .parse(raw)
                .withNano(0)
        }.getOrNull()

    /** Today's scheduled instant for a cached medication (used when "Take" has no tracked occurrence). */
    fun scheduledTodayMs(
        med: ScheduledMed
    ): Long? {

        val t =
            parseTime(med.time)
                ?: return null

        val zone =
            ZoneId.systemDefault()

        return ZonedDateTime
            .of(
                LocalDate.now(zone),
                t,
                zone
            )
            .toInstant()
            .toEpochMilli()
    }
}