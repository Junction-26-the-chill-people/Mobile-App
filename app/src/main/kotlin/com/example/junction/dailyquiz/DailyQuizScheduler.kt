package com.example.junction.dailyquiz

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.junction.sola.data.*
import com.junction.catnudge.CatNudge
import com.junction.catnudge.NudgeCopy
import com.junction.catnudge.NudgeMessages
import com.junction.catnudge.NudgeRequest

/** The host owns timing and completion; the reusable cat owns presentation and screen readiness. */
object DailyQuizScheduler {
    const val ACTION_DUE = "com.example.junction.action.DAILY_QUIZ_DUE"
    const val DESTINATION = "daily_quiz"
    private const val PREFS = "daily_quiz_schedule"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun times(context: Context): DailyQuizTimes =
        SolaStore.personal(context).preferences.let {
            fun time(value: String): QuizTime {
                val parts = value.split(':')
                return QuizTime(parts[0].toInt(), parts[1].toInt())
            }
            DailyQuizTimes(time(it.morning.time), time(it.evening.time), time(it.afternoon.time))
        }

    fun updateTimes(context: Context, times: DailyQuizTimes) {
        val store = SolaStore(context)
        store.useSample(false)
        store.update {
            it.copy(
                preferences =
                    it.preferences.copy(
                        morning = it.preferences.morning.copy(time = times.morning.display()),
                        evening = it.preferences.evening.copy(time = times.evening.display()),
                    )
            )
        }
        reschedule(context)
        reconcile(context)
    }

    private fun anchor(context: Context, now: Long) =
        prefs(context).getString("rhythm_anchor", SolaDates.key(now)).orEmpty()

    @Synchronized
    fun reconcile(context: Context, now: Long = System.currentTimeMillis()) {
        if (!CatNudge.isMonitoringEnabled(context)) return
        val settings = prefs(context)
        val personal = SolaStore.personal(context)
        val issued = settings.getStringSet("issued_slots", emptySet()).orEmpty().toMutableSet()
        val completed = personal.moods.mapNotNull { it.checkIn?.id }.toSet()
        val enabledAt =
            maxOf(settings.getLong("enabled_at", 0L), settings.getLong("rhythm_changed_at", 0L))
        for (slot in
            DailyQuizSchedule.slotsForRhythm(now, personal.preferences, anchor(context, now))) {
            if (
                slot.dueAtMillis > now ||
                    slot.dueAtMillis < enabledAt ||
                    slot.id in issued ||
                    slot.id in completed
            )
                continue
            if (
                CatNudge.enqueue(
                    context,
                    NudgeRequest(
                        id = slot.id,
                        destination = DESTINATION,
                        payload =
                            mapOf(
                                "period" to slot.period.key,
                                "date" to slot.localDate,
                                "scheduled_at" to slot.dueAtMillis.toString(),
                            ),
                        copy = nextCopy(context),
                        waitForReadyScreen = true,
                    ),
                )
            )
                issued.add(slot.id)
        }
        settings.edit().putStringSet("issued_slots", issued.sorted().takeLast(128).toSet()).apply()
    }

    @Synchronized
    fun nextCopy(context: Context): NudgeCopy {
        val settings = prefs(context)
        val index = settings.getInt("copy_sequence", 0).coerceAtLeast(0)
        settings.edit().putInt("copy_sequence", (index + 1) % 120).apply()
        return NudgeCopy(
            NudgeMessages.invitations[index % NudgeMessages.invitations.size],
            NudgeMessages.gentlePleas[index % NudgeMessages.gentlePleas.size],
            NudgeMessages.finalPleas[index % NudgeMessages.finalPleas.size],
        )
    }

    fun onMonitoringChanged(context: Context, enabled: Boolean) {
        if (enabled) {
            val now = System.currentTimeMillis()
            prefs(context)
                .edit()
                .putLong("enabled_at", now)
                .apply {
                    if (!prefs(context).contains("rhythm_anchor"))
                        putString("rhythm_anchor", SolaDates.key(now))
                }
                .apply()
        }
        reschedule(context)
        if (enabled) reconcile(context) else CatNudge.dismiss(context)
    }

    fun onRhythmChanged(context: Context) {
        if (CatNudge.isMonitoringEnabled(context))
            prefs(context).edit().putLong("rhythm_changed_at", System.currentTimeMillis()).apply()
        reschedule(context)
    }

    fun reset(context: Context) {
        CatNudge.stopMonitoring(context)
        CatNudge.dismiss(context)
        prefs(context).edit().clear().apply()
        reschedule(context)
    }

    fun onClockChanged(context: Context, now: Long = System.currentTimeMillis()) {
        val settings = prefs(context)
        settings
            .edit()
            .putLong("enabled_at", settings.getLong("enabled_at", 0L).coerceAtMost(now))
            .putLong(
                "rhythm_changed_at",
                settings.getLong("rhythm_changed_at", 0L).coerceAtMost(now),
            )
            .apply()
    }

    fun reschedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Cancel both original per-period alarms and the new single next-slot alarm.
        for (period in QuizPeriod.entries) alarms.cancel(alarmIntent(context, 100 + period.ordinal))
        val next = alarmIntent(context, 200)
        alarms.cancel(next)
        if (!CatNudge.isMonitoringEnabled(context)) return
        val now = System.currentTimeMillis()
        DailyQuizSchedule.nextRhythmOccurrence(
                now,
                SolaStore.personal(context).preferences,
                anchor(context, now),
            )
            ?.let {
                alarms.set(AlarmManager.RTC, it, next)
            }
    }

    private fun alarmIntent(context: Context, code: Int) =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, DailyQuizReceiver::class.java).setAction(ACTION_DUE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun logMood(
        context: Context,
        store: SolaStore,
        mood: SolaMood,
        now: Long = System.currentTimeMillis(),
    ) {
        if (
            !store.isPreview &&
                !store.state.value.demo &&
                store.state.value.activeCheckIn == null &&
                CatNudge.isMonitoringEnabled(context)
        ) {
            val settings = prefs(context)
            val cutoff =
                maxOf(settings.getLong("enabled_at", 0L), settings.getLong("rhythm_changed_at", 0L))
            val slot =
                DailyQuizSchedule.slotsForRhythm(
                        now,
                        store.state.value.preferences,
                        anchor(context, now),
                    )
                    .lastOrNull { it.dueAtMillis <= now && it.dueAtMillis >= cutoff }
            if (slot != null && !store.state.value.moods.any { it.checkIn?.id == slot.id })
                store.openCheckIn(
                    CheckInSession(slot.id, slot.period.key, slot.localDate, slot.dueAtMillis)
                )
        }
        val id = store.state.value.activeCheckIn?.id
        store.saveMood(mood, now)
        if (!store.isPreview && !store.state.value.demo && id != null) CatNudge.dismiss(context, id)
    }
}
