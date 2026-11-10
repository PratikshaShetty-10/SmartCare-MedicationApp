package com.yourteam.smartcare.escalation

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yourteam.smartcare.data.SupabaseClient
import com.yourteam.smartcare.utils.NotificationHelper
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

/**
 * WorkManager is NOT the clock (AlarmManager is).
 *
 * WorkManager only performs network synchronization:
 *
 *  - ACK:
 *      write TAKEN to adherence_logs using rpc ack_dose
 *
 *  - MISSED:
 *      call the secure Edge Function that atomically marks
 *      MISSED and notifies the guardian.
 */
object DoseSync {

    const val KIND_ACK = "ack"
    const val KIND_MISSED = "missed"

    private const val TAG = "DoseSync"

    private const val K_KIND = "kind"
    private const val K_MED = "med"
    private const val K_SCHED = "sched"
    private const val K_TAKEN = "taken"

    private val online =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    fun enqueueAck(
        ctx: Context,
        medicationId: String,
        scheduledAtMs: Long,
        takenAtMs: Long
    ) {
        val wm = WorkManager.getInstance(ctx)

        val occId =
            DoseStore.occurrenceId(
                medicationId,
                scheduledAtMs
            )

        // Taken before the missed alert was sent
        // (for example, while offline):
        // remove the pending missed synchronization.
        wm.cancelUniqueWork("sync-missed-$occId")

        enqueue(
            wm,
            "sync-ack-$occId",
            KIND_ACK,
            medicationId,
            scheduledAtMs,
            takenAtMs
        )
    }

    fun enqueueMissed(
        ctx: Context,
        occ: DoseOccurrence
    ) {
        Log.d(
            TAG,
            "enqueueMissed(): occurrence=${occ.id}, " +
                    "medication=${occ.medicationId}, " +
                    "scheduledAt=${DoseFormat.isoUtc(occ.scheduledAtMs)}"
        )

        enqueue(
            WorkManager.getInstance(ctx),
            "sync-missed-${occ.id}",
            KIND_MISSED,
            occ.medicationId,
            occ.scheduledAtMs,
            0L
        )
    }

    private fun enqueue(
        wm: WorkManager,
        name: String,
        kind: String,
        med: String,
        sched: Long,
        taken: Long
    ) {
        val req =
            OneTimeWorkRequestBuilder<DoseSyncWorker>()
                .setConstraints(online)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .setExpedited(
                    OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST
                )
                .setInputData(
                    workDataOf(
                        K_KIND to kind,
                        K_MED to med,
                        K_SCHED to sched,
                        K_TAKEN to taken
                    )
                )
                .build()

        // KEEP:
        // the same occurrence never queues the same job twice.
        wm.enqueueUniqueWork(
            name,
            ExistingWorkPolicy.KEEP,
            req
        )

        Log.d(
            TAG,
            "Work enqueued: name=$name, kind=$kind"
        )
    }

    internal fun keys() =
        arrayOf(
            K_KIND,
            K_MED,
            K_SCHED,
            K_TAKEN
        )
}


class DoseSyncWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private val tag = "DoseSyncWorker"

    override suspend fun doWork(): Result {

        Log.d(
            tag,
            "doWork() started, attempt=$runAttemptCount"
        )

        val (kK, kM, kS, kT) =
            DoseSync.keys()

        val kind =
            inputData.getString(kK)
                ?: run {
                    Log.e(
                        tag,
                        "Missing input: kind"
                    )
                    return Result.failure()
                }

        val med =
            inputData.getString(kM)
                ?: run {
                    Log.e(
                        tag,
                        "Missing input: medication"
                    )
                    return Result.failure()
                }

        val sched =
            inputData.getLong(kS, 0L)

        val taken =
            inputData.getLong(kT, 0L)

        Log.d(
            tag,
            "Processing kind=$kind, medication=$med, " +
                    "scheduledAt=${DoseFormat.isoUtc(sched)}"
        )

        return try {

            when (kind) {

                // ==================================================
                // TAKEN
                // ==================================================
                DoseSync.KIND_ACK -> {

                    Log.d(
                        tag,
                        "Calling ack_dose RPC"
                    )

                    val params =
                        buildJsonObject {
                            put(
                                "p_medication_id",
                                med
                            )

                            put(
                                "p_scheduled_at",
                                DoseFormat.isoUtc(sched)
                            )

                            put(
                                "p_taken_at",
                                DoseFormat.isoUtc(taken)
                            )
                        }

                    SupabaseClient.client.postgrest.rpc(
                        "ack_dose",
                        params
                    )

                    Log.d(
                        tag,
                        "ack_dose RPC completed"
                    )
                }

                // ==================================================
                // MISSED
                // ==================================================
                DoseSync.KIND_MISSED -> {

                    Log.d(
                        tag,
                        "Calling missed-dose Edge Function"
                    )

                    val resp =
                        SupabaseClient.client.functions.invoke(
                            "missed-dose",
                            body = buildJsonObject {
                                put(
                                    "medication_id",
                                    med
                                )

                                put(
                                    "scheduled_at",
                                    DoseFormat.isoUtc(sched)
                                )

                                put(
                                    "time_label",
                                    DoseFormat.timeLabel(sched)
                                )
                            }
                        )

                    Log.d(
                        tag,
                        "missed-dose response status=${resp.status}"
                    )

                    if (!resp.status.isSuccess()) {
                        error(
                            "missed-dose HTTP ${resp.status}"
                        )
                    }

                    Log.d(
                        tag,
                        "missed-dose Edge Function completed successfully"
                    )
                }
            }

            Log.d(
                tag,
                "$kind sync completed successfully"
            )

            Result.success()

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            Log.w(
                tag,
                "$kind sync failed " +
                        "(attempt $runAttemptCount): ${e.message}",
                e
            )

            if (runAttemptCount >= 12) {
                Log.e(
                    tag,
                    "$kind sync giving up after maximum retry attempts"
                )

                Result.failure()
            } else {

                Log.d(
                    tag,
                    "$kind sync will retry"
                )

                Result.retry()
            }
        }
    }

    // Only used on Android < 12 for expedited work.
    override suspend fun getForegroundInfo() =
        ForegroundInfo(
            NotificationHelper.NOTIFICATION_ID_DOSE_SERVICE + 1,
            NotificationHelper(applicationContext)
                .buildDoseServiceNotification()
        )
}


/**
 * Re-reads the elder's medications so guardian edits reach
 * the elder's phone without opening the app.
 */
class ScheduleSyncWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private val tag = "ScheduleSyncWorker"

    override suspend fun doWork(): Result {

        Log.d(
            tag,
            "doWork() started, attempt=$runAttemptCount"
        )

        return try {

            EscalationScheduler.syncFromServer(
                applicationContext
            )

            Log.d(
                tag,
                "doWork() finished"
            )

            Result.success()

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            Log.e(
                tag,
                "Schedule sync worker failed: ${e.message}",
                e
            )

            Result.retry()
        }
    }
}


object ScheduleSync {

    private const val TAG = "ScheduleSync"

    private val online =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    fun enqueuePeriodic(ctx: Context) {

        Log.d(
            TAG,
            "enqueuePeriodic() called"
        )

        val req =
            PeriodicWorkRequestBuilder<ScheduleSyncWorker>(
                15,
                TimeUnit.MINUTES
            )
                .setConstraints(online)
                .build()

        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork(
                "smartcare-schedule-sync",
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )

        Log.d(
            TAG,
            "Periodic schedule sync enqueued"
        )
    }

    fun enqueueNow(ctx: Context) {

        Log.d(
            TAG,
            "enqueueNow() called"
        )

        val request =
            OneTimeWorkRequestBuilder<ScheduleSyncWorker>()
                .setConstraints(online)
                .build()

        WorkManager.getInstance(ctx)
            .enqueueUniqueWork(
                "smartcare-schedule-sync-now",
                ExistingWorkPolicy.REPLACE,
                request
            )

        Log.d(
            TAG,
            "One-time schedule sync enqueued"
        )
    }
}