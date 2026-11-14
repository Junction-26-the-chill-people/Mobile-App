package com.example.junction.dailyquiz

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class QuizPeriod(val key: String, val label: String) {
    MORNING("morning", "Morning"), EVENING("evening", "Evening")
}

data class QuizTime(val hour: Int, val minute: Int = 0) {
    init { require(hour in 0..23 && minute in 0..59) }
    fun display(): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
}

data class DailyQuizTimes(
    val morning: QuizTime = QuizTime(10),
    val evening: QuizTime = QuizTime(21),
) {
    fun forPeriod(period: QuizPeriod) = if (period == QuizPeriod.MORNING) morning else evening
}

data class QuizSlot(val id: String, val period: QuizPeriod, val localDate: String, val dueAtMillis: Long)

/** Device-local wall-clock slots. Calendar keeps API 24 support and handles zone/DST changes. */
object DailyQuizSchedule {
    fun slotsForDay(now: Long, times: DailyQuizTimes = DailyQuizTimes(), zone: TimeZone = TimeZone.getDefault()): List<QuizSlot> {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone }.format(now)
        return QuizPeriod.entries.map { period ->
            val time = times.forPeriod(period)
            val calendar = Calendar.getInstance(zone).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, time.hour)
                set(Calendar.MINUTE, time.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            QuizSlot("daily-quiz:$date:${period.key}", period, date, calendar.timeInMillis)
        }
    }

    fun dueSlots(now: Long, times: DailyQuizTimes = DailyQuizTimes(), zone: TimeZone = TimeZone.getDefault()) =
        slotsForDay(now, times, zone).filter { it.dueAtMillis <= now }

    fun nextOccurrence(period: QuizPeriod, now: Long, times: DailyQuizTimes = DailyQuizTimes(), zone: TimeZone = TimeZone.getDefault()): Long {
        val today = slotsForDay(now, times, zone).single { it.period == period }.dueAtMillis
        if (today > now) return today
        return Calendar.getInstance(zone).apply { timeInMillis = today; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
    }
}
