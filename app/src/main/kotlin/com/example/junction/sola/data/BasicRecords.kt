package com.example.junction.sola.data

import android.content.Context
import org.json.JSONObject

data class BasicSampleDay(
    val date: String,
    val mood: Double?,
    val sleepHours: Double?,
    val steps: Double?,
    val missing: Boolean,
)

data class BasicSampleProfile(
    val id: String,
    val name: String,
    val selectedDate: String,
    val days: List<BasicSampleDay>,
    val synthetic: Boolean = true,
)

/**
 * A small readonly projection of the website's synthetic stories; never a patient/clinician API.
 */
object BasicSamples {
    fun load(context: Context): List<BasicSampleProfile> {
        val root =
            JSONObject(
                context.assets.open("sola/basic-log-samples.json").bufferedReader().use {
                    it.readText()
                }
            )
        require(root.getBoolean("synthetic") && root.getInt("schemaVersion") == 1)
        val profiles = root.getJSONArray("profiles")
        fun JSONObject.number(key: String): Double? =
            if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
        return (0 until profiles.length()).map { i ->
            val p = profiles.getJSONObject(i)
            require(p.getBoolean("synthetic") && p.getString("id").matches(Regex("sim-[0-9]{2}")))
            val rows = p.getJSONArray("records")
            BasicSampleProfile(
                p.getString("id"),
                p.getString("name"),
                p.getString("selectedDate"),
                (0 until rows.length()).map { j ->
                    val row = rows.getJSONObject(j)
                    BasicSampleDay(
                        row.getString("date"),
                        row.number("mood"),
                        row.number("sleepHours"),
                        row.number("steps"),
                        row.optBoolean("missing"),
                    )
                },
            )
        }
    }
}

object BasicRecordSummary {
    fun moods(state: SolaState, days: Int, now: Long = System.currentTimeMillis()): List<MoodLog> {
        val first = if (days == 0) "" else SolaDates.key(SolaDates.dayOffset(now, 1 - days))
        return state.moods
            .filter { days == 0 || (SolaDates.key(it.timestamp) >= first && it.timestamp <= now) }
            .sortedByDescending { it.timestamp }
    }

    fun counts(logs: List<MoodLog>): List<Pair<String, Int>> =
        logs
            .groupingBy { it.label }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

    fun sampleDays(profile: BasicSampleProfile, days: Int): List<BasicSampleDay> {
        val end = profile.selectedDate
        val first =
            if (days == 0) ""
            else SolaDates.key(SolaDates.dayOffset(SolaDates.parseKey(end), 1 - days))
        return profile.days
            .filter { it.date >= first && (days == 0 || it.date <= end) }
            .sortedByDescending { it.date }
    }
}
