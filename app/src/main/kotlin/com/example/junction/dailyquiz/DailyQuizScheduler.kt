package com.example.junction.dailyquiz

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.junction.catnudge.CatNudge
import com.junction.catnudge.NudgeCopy
import com.junction.catnudge.NudgeMessages
import com.junction.catnudge.NudgeRequest

/** Host-app policy: the animation library knows nothing about daily quizzes or their times. */
object DailyQuizScheduler {
    const val ACTION_DUE = "com.example.junction.action.DAILY_QUIZ_DUE"
    const val DESTINATION = "daily_quiz"
    private const val PREFS = "daily_quiz_schedule"

    fun times(context: Context): DailyQuizTimes {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DailyQuizTimes(
            QuizTime(prefs.getInt("morning_hour", 10), prefs.getInt("morning_minute", 0)),
            QuizTime(prefs.getInt("evening_hour", 21), prefs.getInt("evening_minute", 0)),
        )
    }

    fun updateTimes(context: Context, times: DailyQuizTimes) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("morning_hour", times.morning.hour).putInt("morning_minute", times.morning.minute)
            .putInt("evening_hour", times.evening.hour).putInt("evening_minute", times.evening.minute).apply()
        reschedule(context)
        reconcile(context)
    }

    /** Called by the running host on startup, screen readiness, and each system minute tick. */
    @Synchronized
    fun reconcile(context: Context, now: Long = System.currentTimeMillis()) {
        if (!CatNudge.isMonitoringEnabled(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val issued = prefs.getStringSet("issued_slots", emptySet()).orEmpty().toMutableSet()
        for (slot in DailyQuizSchedule.dueSlots(now, times(context))) {
            if (slot.id in issued) continue
            val request = NudgeRequest(
                id = slot.id,
                destination = DESTINATION,
                payload = mapOf("period" to slot.period.key, "date" to slot.localDate, "scheduled_at" to slot.dueAtMillis.toString()),
                copy = nextCopy(context),
                waitForReadyScreen = true,
            )
            if (CatNudge.enqueue(context, request)) issued.add(slot.id)
        }
        // Bound deduplication history without dropping any durable pending library requests.
        prefs.edit().putStringSet("issued_slots", issued.sorted().takeLast(64).toSet()).apply()
    }

    @Synchronized
    fun nextCopy(context: Context): NudgeCopy {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val index = prefs.getInt("copy_sequence", 0).coerceAtLeast(0)
        prefs.edit().putInt("copy_sequence", (index + 1) % 120).apply()
        return NudgeCopy(
            NudgeMessages.invitations[index % NudgeMessages.invitations.size],
            NudgeMessages.gentlePleas[index % NudgeMessages.gentlePleas.size],
            NudgeMessages.finalPleas[index % NudgeMessages.finalPleas.size],
        )
    }

    fun onMonitoringChanged(context: Context, enabled: Boolean) {
        reschedule(context)
        if (enabled) reconcile(context) else CatNudge.dismiss(context)
    }

    fun reschedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        for (period in QuizPeriod.entries) {
            val pending = alarmIntent(context, period)
            alarms.cancel(pending)
            if (CatNudge.isMonitoringEnabled(context)) {
                // Non-wakeup/inexact by design: never wake a sleeping phone to show the cat.
                alarms.set(AlarmManager.RTC, DailyQuizSchedule.nextOccurrence(period, System.currentTimeMillis(), times(context)), pending)
            }
        }
    }

    private fun alarmIntent(context: Context, period: QuizPeriod) = PendingIntent.getBroadcast(
        context, 100 + period.ordinal,
        Intent(context, DailyQuizReceiver::class.java).setAction(ACTION_DUE).putExtra("period", period.key),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
