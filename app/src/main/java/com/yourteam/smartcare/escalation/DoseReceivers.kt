package com.yourteam.smartcare.escalation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Fired by AlarmManager for each (occurrence, stage). */
class DoseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        Log.i(
            "DoseAlarmReceiver",
            "onReceive: action=${intent.action}, " +
                    "data=${intent.data}"
        )

        if (intent.action != EscalationScheduler.ACTION_STAGE) {

            Log.w(
                "DoseAlarmReceiver",
                "Ignoring unexpected action=${intent.action}"
            )

            return
        }

        val occId =
            intent.getStringExtra(
                EscalationScheduler.EXTRA_OCC_ID
            )

        val stage =
            intent.getIntExtra(
                EscalationScheduler.EXTRA_STAGE,
                -1
            )

        Log.i(
            "DoseAlarmReceiver",
            "Alarm received: occId=$occId, stage=$stage"
        )

        if (occId == null) {

            Log.e(
                "DoseAlarmReceiver",
                "Missing occurrence ID"
            )

            return
        }

        if (
            stage !in
            EscalationConfig.STAGE_REMINDER..
            EscalationConfig.STAGE_TIMEOUT
        ) {

            Log.e(
                "DoseAlarmReceiver",
                "Invalid stage=$stage for occId=$occId"
            )

            return
        }

        try {

            Log.i(
                "DoseAlarmReceiver",
                "Calling DoseEscalationEngine: " +
                        "occId=$occId, stage=$stage"
            )

            DoseEscalationEngine.onStageAlarm(
                context.applicationContext,
                occId,
                stage
            )

            Log.i(
                "DoseAlarmReceiver",
                "DoseEscalationEngine completed: " +
                        "occId=$occId, stage=$stage"
            )

        } catch (e: Exception) {

            Log.e(
                "DoseAlarmReceiver",
                "Stage $stage failed for $occId",
                e
            )
        }
    }
}

/** AlarmManager forgets alarms on reboot / app update / exact-alarm permission change. */
class DoseBootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        Log.i(
            "DoseBootReceiver",
            "onReceive: action=${intent.action}"
        )

        when (intent.action) {

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> {

                Log.i(
                    "DoseBootReceiver",
                    "Recovering escalation alarms and schedule"
                )

                EscalationScheduler.recoverAfterRestart(
                    context.applicationContext
                )

                ScheduleSync.enqueueNow(
                    context.applicationContext
                )
            }
        }
    }
}