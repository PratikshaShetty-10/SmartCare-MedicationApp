package com.yourteam.smartcare.escalation

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DoseStatus {
    const val PENDING = "pending"
    const val TAKEN = "taken"
    const val MISSED = "missed"
}

/** One dose of one medication at one exact time. id = medicationId + scheduled epoch ms. */
@Serializable
data class DoseOccurrence(
    val id: String,
    val medicationId: String,
    val medName: String,
    val dosage: String = "",
    val elderName: String? = null,
    val scheduledAtMs: Long,
    val status: String = DoseStatus.PENDING,
    /** Highest stage already executed (0 = none). Makes every stage idempotent. */
    val stage: Int = 0
) {
    private val who get() = elderName?.takeIf { it.isNotBlank() }

    fun reminderSentence() =
        if (who != null) "$who, it's time to take your $medName." else "It's time to take your $medName."

    fun finalSentence() =
        if (who != null) "$who, please take your $medName right now." else "Please take your $medName right now."
}

/** Cached copy of the elder's medication schedule so alarms work offline / after reboot. */
@Serializable
data class ScheduledMed(
    val medicationId: String,
    val medName: String,
    val dosage: String,
    val time: String
)

object DoseFormat {
    fun timeLabel(ms: Long): String =
        DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    fun isoUtc(ms: Long): String = Instant.ofEpochMilli(ms).toString()
}

/** Tiny SharedPreferences-backed store. All state transitions are atomic under one lock. */
object DoseStore {
    private const val PREFS = "smartcare_dose_state"
    private const val OCC = "occ:"
    private const val KEY_SCHEDULE = "schedule"
    private const val KEY_ELDER_NAME = "elder_name"
    private const val RETENTION_MS = 2L * 24 * 60 * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun occurrenceId(medicationId: String, scheduledAtMs: Long) = "${medicationId}_$scheduledAtMs"

    private fun decode(s: String): DoseOccurrence? =
        runCatching { json.decodeFromString(DoseOccurrence.serializer(), s) }.getOrNull()

    private fun read(ctx: Context, id: String): DoseOccurrence? =
        prefs(ctx).getString(OCC + id, null)?.let(::decode)

    private fun write(ctx: Context, occ: DoseOccurrence) {
        prefs(ctx).edit()
            .putString(OCC + occ.id, json.encodeToString(DoseOccurrence.serializer(), occ))
            .commit()
    }

    fun get(ctx: Context, id: String): DoseOccurrence? = synchronized(lock) { read(ctx, id) }

    fun all(ctx: Context): List<DoseOccurrence> = synchronized(lock) {
        prefs(ctx).all.filterKeys { it.startsWith(OCC) }.values
            .mapNotNull { (it as? String)?.let(::decode) }
    }

    /** @return true if created, false if this exact occurrence already existed (duplicate scheduling). */
    fun putIfAbsent(ctx: Context, occ: DoseOccurrence): Boolean = synchronized(lock) {
        if (read(ctx, occ.id) != null) false else { write(ctx, occ); true }
    }

    fun delete(ctx: Context, id: String) = synchronized(lock) {
        prefs(ctx).edit().remove(OCC + id).commit()
    }

    /** Atomic status change; returns true only for the caller that actually performed it. */
    fun transition(ctx: Context, id: String, from: Set<String>, to: String): Boolean = synchronized(lock) {
        val occ = read(ctx, id) ?: return false
        if (occ.status !in from) return false
        write(ctx, occ.copy(status = to))
        true
    }

    /** Moves stage forward only (never backwards) while still PENDING. */
    fun advanceStage(ctx: Context, id: String, stage: Int): Boolean = synchronized(lock) {
        val occ = read(ctx, id) ?: return false
        if (occ.status != DoseStatus.PENDING || occ.stage >= stage) return false
        write(ctx, occ.copy(stage = stage))
        true
    }

    /** Picks the dose a "Take" press refers to. */
    fun findForAck(ctx: Context, medicationId: String, nowMs: Long): DoseOccurrence? = synchronized(lock) {
        val mine = all(ctx).filter { it.medicationId == medicationId }
        mine.filter { it.status == DoseStatus.PENDING && it.scheduledAtMs <= nowMs }
            .maxByOrNull { it.scheduledAtMs }
            ?: mine.filter {
                it.status == DoseStatus.PENDING && it.scheduledAtMs > nowMs &&
                        it.scheduledAtMs - nowMs <= EscalationConfig.EARLY_ACK_WINDOW_MS
            }.minByOrNull { it.scheduledAtMs }
            ?: mine.filter {
                it.status == DoseStatus.MISSED && nowMs - it.scheduledAtMs <= EscalationConfig.LATE_ACK_WINDOW_MS
            }.maxByOrNull { it.scheduledAtMs }
    }

    fun prune(ctx: Context, nowMs: Long) = synchronized(lock) {
        all(ctx).filter { it.status != DoseStatus.PENDING && nowMs - it.scheduledAtMs > RETENTION_MS }
            .forEach { prefs(ctx).edit().remove(OCC + it.id).commit() }
    }

    fun saveSchedule(ctx: Context, list: List<ScheduledMed>) {
        prefs(ctx).edit()
            .putString(KEY_SCHEDULE, json.encodeToString(ListSerializer(ScheduledMed.serializer()), list))
            .apply()
    }

    fun loadSchedule(ctx: Context): List<ScheduledMed> =
        prefs(ctx).getString(KEY_SCHEDULE, null)?.let {
            runCatching { json.decodeFromString(ListSerializer(ScheduledMed.serializer()), it) }.getOrNull()
        } ?: emptyList()

    fun saveElderName(ctx: Context, name: String) = prefs(ctx).edit().putString(KEY_ELDER_NAME, name).apply()
    fun loadElderName(ctx: Context): String? = prefs(ctx).getString(KEY_ELDER_NAME, null)
}
