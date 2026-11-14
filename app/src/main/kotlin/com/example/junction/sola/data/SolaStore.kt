package com.example.junction.sola.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local prototype data. Sample mode has a separate record and never replaces a real journal.
 */
class SolaStore(context: Context, preview: SolaState? = null) {
    companion object {
        const val PREFS = "sola_local_v1"

        fun personal(context: Context): SolaState = runCatching {
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("personal", null)
                ?.let(SolaJson::decode) ?: SolaState()
        }
            .getOrDefault(SolaState())
            .copy(demo = false)
    }

    private val filesDir = context.filesDir
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val ephemeral = preview != null
    private var sample = preview?.demo ?: prefs.getBoolean("sample_mode", false)
    private val mutable = MutableStateFlow(preview ?: read(sample))
    val state = mutable.asStateFlow()
    private val changes =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == null || key == "sample_mode" || key == if (sample) "sample" else "personal"
            ) {
                sample = prefs.getBoolean("sample_mode", false)
                mutable.value = read(sample)
            }
        }

    init {
        if (!ephemeral) prefs.registerOnSharedPreferenceChangeListener(changes)
    }

    val isPreview: Boolean
        get() = ephemeral

    fun openCheckIn(session: CheckInSession): Boolean {
        if (sample) useSample(false)
        if (!ephemeral) mutable.value = read(sample)
        if (state.value.moods.any { it.checkIn?.id == session.id }) return false
        update { it.copy(activeCheckIn = session) }
        return true
    }

    private fun read(demo: Boolean): SolaState = runCatching {
        val text = prefs.getString(if (demo) "sample" else "personal", null)
        if (text == null) {
            if (demo) SolaContent.demo() else SolaState()
        } else SolaJson.decode(text)
    }
        .getOrElse { if (demo) SolaContent.demo() else SolaState() }

    @Synchronized
    fun update(change: (SolaState) -> SolaState) {
        val previous = if (ephemeral) mutable.value else read(sample)
        val next = change(previous).copy(demo = sample)
        if (next == previous) {
            mutable.value = next
            return
        }
        if (!ephemeral)
            prefs
                .edit()
                .putString(if (sample) "sample" else "personal", SolaJson.encode(next))
                .apply()
        if (!ephemeral) {
            fun photos(state: SolaState) =
                state.diary.mapNotNull { it.photo } + listOfNotNull(state.draftPhoto)
            val kept = (photos(next) + photos(read(!sample))).toSet()
            photos(previous)
                .distinct()
                .filterNot { it in kept }
                .forEach { relative ->
                    if (relative.matches(Regex("sola-photos/[0-9a-fA-F-]{36}\\.jpg")))
                        java.io.File(filesDir, relative).delete()
                }
        }
        mutable.value = next
    }

    @Synchronized
    fun useSample(enabled: Boolean) {
        sample = enabled
        if (!ephemeral) prefs.edit().putBoolean("sample_mode", enabled).apply()
        mutable.value =
            if (ephemeral) {
                if (enabled) SolaContent.demo() else SolaState()
            } else read(enabled)
    }

    fun saveMood(mood: SolaMood, now: Long = System.currentTimeMillis()) = update {
        val active = it.activeCheckIn
        if (active != null && it.moods.any { mood -> mood.checkIn?.id == active.id })
            return@update it.copy(activeCheckIn = null)
        it.copy(
            moods =
                it.moods +
                    MoodLog(
                        timestamp = now,
                        value = mood.value,
                        label = mood.label,
                        quadrant = mood.quadrant,
                        checkIn = active,
                    ),
            activeCheckIn = null,
        )
    }

    fun saveDiary(
        voice: Boolean,
        summary: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (state.value.diaryDraft.isBlank() && state.value.draftPhoto == null) return false
        update {
            it.copy(
                diary =
                    it.diary +
                        DiaryNote(
                            timestamp = now,
                            text = it.diaryDraft.trim(),
                            photo = it.draftPhoto,
                            voice = voice,
                            summary = summary,
                        ),
                diaryDraft = "",
                draftPhoto = null,
            )
        }
        return true
    }

    fun resetCurrent() {
        if (sample) update { SolaContent.demo() } else update { SolaState() }
    }
}

/**
 * Explicit versioned JSON keeps this feature independent of a future shared database/auth schema.
 */
object SolaJson {
    private fun session(value: CheckInSession?) = value?.let {
        obj(
            "id" to it.id,
            "period" to it.period,
            "date" to it.date,
            "scheduledAt" to it.scheduledAt,
        )
    }

    private fun readSession(value: JSONObject?): CheckInSession? =
        value
            ?.takeIf { it.optString("id").isNotBlank() }
            ?.let {
                CheckInSession(
                    it.getString("id"),
                    it.optString("period"),
                    it.optString("date"),
                    if (it.isNull("scheduledAt")) null else it.optLong("scheduledAt"),
                )
            }

    private fun obj(vararg fields: Pair<String, Any?>) =
        JSONObject().apply { fields.forEach { put(it.first, it.second ?: JSONObject.NULL) } }

    private fun strings(values: Collection<String>) = JSONArray(values.toList())

    private fun <T> rows(values: List<T>, block: (T) -> JSONObject) =
        JSONArray().apply { values.forEach { put(block(it)) } }

    private fun JSONObject.strings(key: String): List<String> =
        optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()

    private fun <T> JSONObject.rows(key: String, block: (JSONObject) -> T): List<T> =
        optJSONArray(key)?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(block) }
        } ?: emptyList()

    private fun JSONObject.nullable(key: String) =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun window(value: CheckInWindow) = obj("count" to value.count, "time" to value.time)

    fun encode(s: SolaState): String =
        obj(
                "version" to 1,
                "onboardingComplete" to s.onboardingComplete,
                "activeCheckIn" to session(s.activeCheckIn),
                "demo" to s.demo,
                "profile" to
                    s.profile.let {
                        obj(
                            "name" to it.name,
                            "email" to it.email,
                            "telegram" to it.telegram,
                            "telegramId" to it.telegramId,
                            "clinic" to it.clinic,
                            "clinicPhone" to it.clinicPhone,
                            "clinicianSharing" to it.clinicianSharing,
                        )
                    },
                "interests" to strings(s.interests),
                "customInterests" to strings(s.customInterests),
                "goals" to strings(s.goals),
                "customGoals" to strings(s.customGoals),
                "completedGoals" to strings(s.completedGoals),
                "preferences" to
                    s.preferences.let {
                        obj(
                            "cadence" to it.cadence,
                            "morning" to window(it.morning),
                            "afternoon" to window(it.afternoon),
                            "evening" to window(it.evening),
                            "ideaDirection" to it.ideaDirection,
                            "energyTier" to it.energyTier,
                            "includeFriends" to it.includeFriends,
                            "showIdeas" to it.showIdeas,
                            "sources" to strings(it.selectedSources),
                            "backendUrl" to it.backendUrl,
                        )
                    },
                "friends" to
                    rows(s.friends) {
                        obj(
                            "id" to it.id,
                            "name" to it.name,
                            "telegramId" to it.telegramId,
                            "telegram" to it.telegram,
                            "phone" to it.phone,
                        )
                    },
                "moods" to
                    rows(s.moods) {
                        obj(
                            "id" to it.id,
                            "timestamp" to it.timestamp,
                            "value" to it.value,
                            "label" to it.label,
                            "quadrant" to it.quadrant,
                            "checkIn" to session(it.checkIn),
                        )
                    },
                "diary" to
                    rows(s.diary) {
                        obj(
                            "id" to it.id,
                            "timestamp" to it.timestamp,
                            "text" to it.text,
                            "photo" to it.photo,
                            "voice" to it.voice,
                            "summary" to it.summary,
                        )
                    },
                "tasks" to
                    rows(s.tasks) {
                        obj(
                            "id" to it.id,
                            "timestamp" to it.timestamp,
                            "title" to it.title,
                            "done" to it.done,
                        )
                    },
                "butterflies" to
                    rows(s.butterflies) {
                        obj(
                            "id" to it.id,
                            "friend" to it.friend,
                            "message" to it.message,
                            "receivedAt" to it.receivedAt,
                        )
                    },
                "monthlyIntention" to s.monthlyIntention,
                "diaryDraft" to s.diaryDraft,
                "draftPhoto" to s.draftPhoto,
            )
            .toString()

    fun decode(text: String): SolaState {
        val o = JSONObject(text)
        require(o.optInt("version") == 1) { "Unsupported Sola data version" }
        val p = o.optJSONObject("profile") ?: JSONObject()
        val r = o.optJSONObject("preferences") ?: JSONObject()
        fun readWindow(key: String, default: CheckInWindow): CheckInWindow =
            r.optJSONObject(key)?.let {
                val time = it.optString("time", default.time)
                CheckInWindow(
                    it.optInt("count", default.count).coerceIn(0, 3),
                    time.takeIf { v -> v.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) }
                        ?: default.time,
                )
            } ?: default
        return SolaState(
            onboardingComplete = o.optBoolean("onboardingComplete"),
            demo = o.optBoolean("demo"),
            profile =
                SolaProfile(
                    p.optString("name"),
                    p.optString("email"),
                    p.optString("telegram"),
                    p.optString("telegramId"),
                    p.optString("clinic"),
                    p.optString("clinicPhone"),
                    p.optBoolean("clinicianSharing"),
                ),
            interests = o.strings("interests"),
            customInterests = o.strings("customInterests"),
            goals = o.strings("goals"),
            customGoals = o.strings("customGoals"),
            completedGoals = o.strings("completedGoals").toSet(),
            preferences =
                SolaPreferences(
                    r.optString("cadence", "Every day"),
                    readWindow("morning", CheckInWindow(1, "10:00")),
                    readWindow("afternoon", CheckInWindow(0, "14:00")),
                    readWindow("evening", CheckInWindow(1, "21:00")),
                    r.optString("ideaDirection", "balanced"),
                    r.optString("energyTier", "BIGGER"),
                    r.optBoolean("includeFriends", true),
                    r.optBoolean("showIdeas", true),
                    r.strings("sources").toSet(),
                    r.optString("backendUrl"),
                ),
            friends =
                o.rows("friends") {
                    SolaFriend(
                        it.getString("id"),
                        it.optString("name"),
                        it.optString("telegramId"),
                        it.optString("telegram"),
                        it.optString("phone"),
                    )
                },
            moods =
                o.rows("moods") {
                    MoodLog(
                        it.getString("id"),
                        it.getLong("timestamp"),
                        it.optInt("value").coerceIn(0, 100),
                        it.optString("label"),
                        it.optString("quadrant"),
                        readSession(it.optJSONObject("checkIn")),
                    )
                },
            diary =
                o.rows("diary") {
                    DiaryNote(
                        it.getString("id"),
                        it.getLong("timestamp"),
                        it.optString("text"),
                        it.nullable("photo"),
                        it.optBoolean("voice"),
                        it.optBoolean("summary"),
                    )
                },
            tasks =
                o.rows("tasks") {
                    SolaTask(
                        it.getString("id"),
                        it.getLong("timestamp"),
                        it.optString("title"),
                        it.optBoolean("done"),
                    )
                },
            butterflies =
                o.rows("butterflies") {
                    Butterfly(
                        it.getString("id"),
                        it.optString("friend"),
                        it.optString("message"),
                        it.optLong("receivedAt"),
                    )
                },
            monthlyIntention = o.optString("monthlyIntention"),
            activeCheckIn = readSession(o.optJSONObject("activeCheckIn")),
            diaryDraft = o.optString("diaryDraft"),
            draftPhoto = o.nullable("draftPhoto"),
        )
    }
}
