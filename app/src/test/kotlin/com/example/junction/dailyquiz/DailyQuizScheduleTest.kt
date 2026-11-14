package com.example.junction.dailyquiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class DailyQuizScheduleTest {
    private val zone = TimeZone.getTimeZone("Europe/Bucharest")

    @Test fun defaultsAreTenInTheMorningAndNineInTheEvening() {
        val times = DailyQuizTimes()
        assertEquals("10:00", times.morning.display())
        assertEquals("21:00", times.evening.display())
        val slots = DailyQuizSchedule.slotsForDay(at(2026, 11, 14, 12), zone = zone)
        assertEquals(listOf(at(2026, 11, 14, 10), at(2026, 11, 14, 21)), slots.map { it.dueAtMillis })
    }

    @Test fun aSlotBecomesDueAtItsExactLocalTimeAndStaysDueForTheRestOfTheDay() {
        fun periods(now: Long) = DailyQuizSchedule.dueSlots(now, zone = zone).map { it.period }
        assertTrue(periods(at(2026, 11, 14, 10) - 1).isEmpty())
        assertEquals(listOf(QuizPeriod.MORNING), periods(at(2026, 11, 14, 10)))
        assertEquals(listOf(QuizPeriod.MORNING), periods(at(2026, 11, 14, 16)))
        assertEquals(listOf(QuizPeriod.MORNING), periods(at(2026, 11, 14, 21) - 1))
        assertEquals(listOf(QuizPeriod.MORNING, QuizPeriod.EVENING), periods(at(2026, 11, 14, 21)))
    }

    @Test fun theNextOccurrenceIsStrictlyFutureAndUsesTomorrowAfterTheSlotIsDue() {
        assertEquals(at(2026, 11, 14, 10), DailyQuizSchedule.nextOccurrence(
            QuizPeriod.MORNING, at(2026, 11, 14, 9, 59), zone = zone))
        assertEquals(at(2026, 11, 15, 10), DailyQuizSchedule.nextOccurrence(
            QuizPeriod.MORNING, at(2026, 11, 14, 10), zone = zone))
        assertEquals(at(2026, 11, 14, 21), DailyQuizSchedule.nextOccurrence(
            QuizPeriod.EVENING, at(2026, 11, 14, 10), zone = zone))
    }

    @Test fun tomorrowKeepsTheLocalHourAcrossTheSpringClockChange() {
        val now = at(2026, 3, 28, 10)
        val next = DailyQuizSchedule.nextOccurrence(QuizPeriod.MORNING, now, zone = zone)
        assertEquals(at(2026, 3, 29, 10), next)
        assertEquals(TimeUnit.HOURS.toMillis(23), next - now)
    }

    @Test fun tomorrowKeepsTheLocalHourAcrossTheAutumnClockChange() {
        val now = at(2026, 10, 24, 10)
        val next = DailyQuizSchedule.nextOccurrence(QuizPeriod.MORNING, now, zone = zone)
        assertEquals(at(2026, 10, 25, 10), next)
        assertEquals(TimeUnit.HOURS.toMillis(25), next - now)
    }

    @Test fun stableIdsUseTheRequestedLocalDateAndPeriod() {
        val instant = at(2026, 11, 15, 1, 30)
        val local = DailyQuizSchedule.slotsForDay(instant, zone = zone)
        assertEquals(listOf("daily-quiz:2026-11-15:morning", "daily-quiz:2026-11-15:evening"), local.map { it.id })
        assertEquals(listOf("2026-11-15", "2026-11-15"), local.map { it.localDate })
        assertEquals(local.map { it.id }, DailyQuizSchedule.slotsForDay(at(2026, 11, 15, 23), zone = zone).map { it.id })
        val utc = DailyQuizSchedule.slotsForDay(instant, zone = TimeZone.getTimeZone("UTC"))
        assertEquals("2026-11-14", utc.first().localDate)
        assertTrue(utc.first().id != local.first().id)
    }

    @Test fun hostConfiguredTimesIncludeTheirMinutes() {
        val times = DailyQuizTimes(QuizTime(8, 15), QuizTime(19, 45))
        assertEquals("08:15", times.morning.display())
        assertTrue(DailyQuizSchedule.dueSlots(at(2026, 11, 14, 8, 14), times, zone).isEmpty())
        assertEquals(listOf(QuizPeriod.MORNING), DailyQuizSchedule.dueSlots(at(2026, 11, 14, 8, 15), times, zone).map { it.period })
        assertEquals(listOf(QuizPeriod.MORNING, QuizPeriod.EVENING), DailyQuizSchedule.dueSlots(at(2026, 11, 14, 19, 45), times, zone).map { it.period })
        assertEquals(at(2026, 11, 15, 19, 45), DailyQuizSchedule.nextOccurrence(
            QuizPeriod.EVENING, at(2026, 11, 14, 20), times, zone))
    }

    @Test fun invalidClockTimesAreRejected() {
        for (time in listOf(-1 to 0, 24 to 0, 10 to -1, 10 to 60)) {
            assertThrows(IllegalArgumentException::class.java) { QuizTime(time.first, time.second) }
        }
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.of(zone.id)).toInstant().toEpochMilli()
}
