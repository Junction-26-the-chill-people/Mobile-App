package com.example.junction.sola

import com.example.junction.sola.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BasicRecordsTest {
    @Test
    fun theBundledProjectionContainsOnlyTheElevenFictionalProfilesAndBasicFields() {
        val context = RuntimeEnvironment.getApplication()
        val json =
            JSONObject(
                context.assets.open("sola/basic-log-samples.json").bufferedReader().use {
                    it.readText()
                }
            )
        assertTrue(json.getBoolean("synthetic"))
        assertEquals(5, json.getJSONObject("moodScale").getInt("maximum"))
        assertEquals(64, json.getString("sourceSha256").length)
        val rows = json.getJSONArray("profiles")
        assertEquals(11, rows.length())
        repeat(rows.length()) { i ->
            val profile = rows.getJSONObject(i)
            assertEquals(
                setOf("id", "name", "synthetic", "selectedDate", "records"),
                profile.keys().asSequence().toSet(),
            )
            assertTrue(profile.getBoolean("synthetic"))
            assertTrue(profile.getString("id").startsWith("sim-"))
            val records = profile.getJSONArray("records")
            assertEquals(120, records.length())
            repeat(records.length()) { j ->
                val record = records.getJSONObject(j)
                assertEquals(
                    setOf("date", "mood", "sleepHours", "steps", "missing"),
                    record.keys().asSequence().toSet(),
                )
                if (!record.isNull("mood")) assertTrue(record.getDouble("mood") in 1.0..5.0)
            }
        }
        val profiles = BasicSamples.load(context)
        assertEquals(
            listOf(
                "Mara",
                "Noah",
                "Lea",
                "Oskar",
                "Iris",
                "Emil",
                "Ada",
                "Jonas",
                "Sofia",
                "Elias",
                "Mila",
            ),
            profiles.map { it.name },
        )
        assertEquals(1320, profiles.sumOf { it.days.size })
        assertTrue(profiles.flatMap { it.days }.any { it.missing && it.mood == null })
    }

    @Test
    fun personalCountsKeepEveryFeelingAndEveryCheckInWithLocalCalendarRanges() {
        val now = SolaDates.parseKey("2026-11-15") + 12 * 60 * 60 * 1000
        fun log(days: Int, label: String) =
            MoodLog(
                timestamp = SolaDates.dayOffset(now, days),
                value = 50,
                label = label,
                quadrant = "sage",
            )
        val state =
            SolaState(
                moods =
                    listOf(
                        log(-7, "Sad"),
                        log(-6, "Calm"),
                        log(0, "Happy"),
                        log(0, "Calm"),
                        log(1, "Safe"),
                    )
            )
        val week = BasicRecordSummary.moods(state, 7, now)
        assertEquals(3, week.size)
        assertEquals(listOf("Calm" to 2, "Happy" to 1), BasicRecordSummary.counts(week))
        assertEquals(5, BasicRecordSummary.moods(state, 0, now).size)
        assertEquals(2, week.map { SolaDates.key(it.timestamp) }.distinct().size)
    }

    @Test
    fun sampleRangesStayOnSourceDatesAndKeepMissingDaysRatherThanInventingMoods() {
        val profiles = BasicSamples.load(RuntimeEnvironment.getApplication())
        val week = BasicRecordSummary.sampleDays(profiles.first(), 7)
        assertEquals(7, week.size)
        assertEquals(profiles.first().selectedDate, week.first().date)
        assertEquals(28, BasicRecordSummary.sampleDays(profiles.first(), 28).size)
        assertEquals(120, BasicRecordSummary.sampleDays(profiles.first(), 0).size)
        assertTrue(week.zipWithNext().all { (a, b) -> a.date > b.date })
        assertTrue(profiles.flatMap { it.days }.all { it.date.startsWith("2026-") })
    }
}
