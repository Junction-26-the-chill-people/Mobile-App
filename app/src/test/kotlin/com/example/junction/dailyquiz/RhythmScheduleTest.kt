package com.example.junction.dailyquiz

import com.example.junction.sola.data.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class RhythmScheduleTest {
    private val zone = TimeZone.getTimeZone("Europe/Bucharest")

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.of(zone.id)).toInstant().toEpochMilli()

    private fun slots(now: Long, p: SolaPreferences) =
        DailyQuizSchedule.slotsForRhythm(now, p, "2026-11-13", zone)

    @Test
    fun nativeDefaultsMatchTheMorningAndEveningWidgetContract() {
        assertEquals(
            listOf(at(2026, 11, 13, 10), at(2026, 11, 13, 21)),
            slots(at(2026, 11, 13), SolaPreferences()).map { it.dueAtMillis },
        )
    }

    @Test
    fun allThreeWindowsHonorMinutesAndHourlyRepeatCounts() {
        val p =
            SolaPreferences(
                morning = CheckInWindow(2, "08:15"),
                afternoon = CheckInWindow(1, "14:30"),
                evening = CheckInWindow(0, "21:00"),
            )
        assertEquals(
            listOf(at(2026, 11, 13, 8, 15), at(2026, 11, 13, 9, 15), at(2026, 11, 13, 14, 30)),
            slots(at(2026, 11, 13), p).map { it.dueAtMillis },
        )
        assertEquals("daily-quiz:2026-11-13:morning:2", slots(at(2026, 11, 13), p)[1].id)
    }

    @Test
    fun weekdaysSkipTheWeekendAndScheduleMonday() {
        val p = SolaPreferences(cadence = "Weekdays")
        assertTrue(slots(at(2026, 11, 14), p).isEmpty())
        assertEquals(
            at(2026, 11, 16, 10),
            DailyQuizSchedule.nextRhythmOccurrence(at(2026, 11, 14), p, "2026-11-13", zone),
        )
    }

    @Test
    fun everyOtherDayUsesADurableLocalDateAnchor() {
        val p = SolaPreferences(cadence = "Every 2 days")
        assertEquals(2, slots(at(2026, 11, 13), p).size)
        assertTrue(slots(at(2026, 11, 14), p).isEmpty())
        assertEquals(2, slots(at(2026, 11, 15), p).size)
    }

    @Test
    fun everyOtherDayStillCountsCalendarDaysAcrossDst() {
        val p = SolaPreferences(cadence = "Every 2 days")
        assertTrue(
            DailyQuizSchedule.slotsForRhythm(at(2026, 3, 29), p, "2026-03-28", zone).isEmpty()
        )
        assertEquals(
            2,
            DailyQuizSchedule.slotsForRhythm(at(2026, 3, 30), p, "2026-03-28", zone).size,
        )
    }

    @Test
    fun countsDoNotSpillIntoTomorrowAndOverlappingTimesShareOneVisit() {
        val p =
            SolaPreferences(
                morning = CheckInWindow(1, "23:10"),
                afternoon = CheckInWindow(0, "14:00"),
                evening = CheckInWindow(3, "23:10"),
            )
        assertEquals(1, slots(at(2026, 11, 13), p).size)
    }

    @Test
    fun noSelectedWindowsMeansNoAlarms() {
        val p =
            SolaPreferences(
                morning = CheckInWindow(0, "10:00"),
                evening = CheckInWindow(0, "21:00"),
            )
        assertNull(DailyQuizSchedule.nextRhythmOccurrence(at(2026, 11, 13), p, "2026-11-13", zone))
    }

    @Test
    fun futureAlarmsUseLocalHoursThroughBothClockChanges() {
        for ((m, d) in listOf(3 to 28, 10 to 24)) {
            val now = at(2026, m, d, 22)
            assertEquals(
                at(2026, m, d + 1, 10),
                DailyQuizSchedule.nextRhythmOccurrence(now, SolaPreferences(), "2026-01-01", zone),
            )
        }
    }
}
