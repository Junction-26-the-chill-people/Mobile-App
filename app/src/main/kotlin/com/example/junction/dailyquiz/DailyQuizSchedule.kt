package com.example.junction.dailyquiz

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class QuizPeriod(val key: String, val label: String) {
    MORNING("morning", "Morning"),
    EVENING("evening", "Evening"),
    AFTERNOON("afternoon", "Afternoon"),
}

data class QuizTime(val hour: Int, val minute: Int = 0) {
    init {
        require(hour in 0..23 && minute in 0..59)
    }

    fun display(): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
}

data class DailyQuizTimes(
    val morning: QuizTime = QuizTime(10),
    val evening: QuizTime = QuizTime(21),
    val afternoon: QuizTime = QuizTime(14),
) {
    fun forPeriod(period: QuizPeriod) =
        when (period) {
            QuizPeriod.MORNING -> morning
            QuizPeriod.EVENING -> evening
            QuizPeriod.AFTERNOON -> afternoon
        }
}

data class QuizSlot(
    val id: String,
    val period: QuizPeriod,
    val localDate: String,
    val dueAtMillis: Long,
)

/** Device-local wall-clock slots. Calendar keeps API 24 support and handles zone/DST changes. */
object DailyQuizSchedule {
    fun slotsForDay(
        now: Long,
        times: DailyQuizTimes = DailyQuizTimes(),
        zone: TimeZone = TimeZone.getDefault(),
    ): List<QuizSlot> {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone }.format(now)
        return listOf(QuizPeriod.MORNING, QuizPeriod.EVENING).map { period ->
            val time = times.forPeriod(period)
            val calendar =
                Calendar.getInstance(zone).apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, time.hour)
                    set(Calendar.MINUTE, time.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            QuizSlot("daily-quiz:$date:${period.key}", period, date, calendar.timeInMillis)
        }
    }

    fun dueSlots(
        now: Long,
        times: DailyQuizTimes = DailyQuizTimes(),
        zone: TimeZone = TimeZone.getDefault(),
    ) = slotsForDay(now, times, zone).filter { it.dueAtMillis <= now }

    fun nextOccurrence(
        period: QuizPeriod,
        now: Long,
        times: DailyQuizTimes = DailyQuizTimes(),
        zone: TimeZone = TimeZone.getDefault(),
    ): Long {
        val today = slotsForDay(now, times, zone).single { it.period == period }.dueAtMillis
        if (today > now) return today
        return Calendar.getInstance(zone)
            .apply {
                timeInMillis = today
                add(Calendar.DAY_OF_YEAR, 1)
            }
            .timeInMillis
    }

    /**
     * A count above one repeats hourly from the selected time; duplicate wall-clock times merge.
     */
    fun slotsForRhythm(
        now: Long,
        preferences: com.example.junction.sola.data.SolaPreferences,
        anchorDate: String,
        zone: TimeZone = TimeZone.getDefault(),
    ): List<QuizSlot> {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = now }
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone }
        val date = format.format(now)
        if (
            preferences.cadence == "Weekdays" &&
                calendar.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        )
            return emptyList()
        if (preferences.cadence == "Every 2 days") {
            // UTC calendar dates count local calendar days, not 24-hour durations across DST.
            val utc =
                SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
            val day =
                runCatching { utc.parse(date)!!.time / 86_400_000L }.getOrNull()
                    ?: return emptyList()
            val anchor =
                runCatching { utc.parse(anchorDate)!!.time / 86_400_000L }.getOrNull() ?: day
            if (Math.floorMod(day - anchor, 2L) != 0L) return emptyList()
        }
        return listOf(
                QuizPeriod.MORNING to preferences.morning,
                QuizPeriod.AFTERNOON to preferences.afternoon,
                QuizPeriod.EVENING to preferences.evening,
            )
            .flatMap { (period, window) ->
                val parts = window.time.split(':')
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: return@flatMap emptyList()
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: return@flatMap emptyList()
                if (hour !in 0..23 || minute !in 0..59) return@flatMap emptyList()
                (0 until window.count.coerceIn(0, 3)).mapNotNull { index ->
                    if (hour + index > 23) return@mapNotNull null
                    val due =
                        (calendar.clone() as Calendar)
                            .apply {
                                set(Calendar.HOUR_OF_DAY, hour + index)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            .timeInMillis
                    QuizSlot(
                        "daily-quiz:$date:${period.key}" + if (index == 0) "" else ":${index + 1}",
                        period,
                        date,
                        due,
                    )
                }
            }
            .sortedBy { it.dueAtMillis }
            .distinctBy { it.dueAtMillis }
    }

    fun nextRhythmOccurrence(
        now: Long,
        preferences: com.example.junction.sola.data.SolaPreferences,
        anchorDate: String,
        zone: TimeZone = TimeZone.getDefault(),
    ): Long? {
        val day = Calendar.getInstance(zone).apply { timeInMillis = now }
        repeat(8) {
            slotsForRhythm(day.timeInMillis, preferences, anchorDate, zone)
                .firstOrNull { it.dueAtMillis > now }
                ?.let {
                    return it.dueAtMillis
                }
            day.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }
}
