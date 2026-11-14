package com.example.junction.sola.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class SolaScreen {
    WELCOME,
    ACCOUNT,
    INTERESTS,
    GOALS,
    CONNECT,
    RHYTHM,
    FRIEND,
    TODAY,
    CHECK_IN,
    GARDEN,
    DIARY,
    STATISTICS,
    SUPPORT,
    RECAP,
    SETTINGS;

    companion object {
        fun fromPreview(value: String?): SolaScreen? = entries.firstOrNull {
            it.name.equals(value?.replace('-', '_'), true)
        }
    }
}

data class SolaProfile(
    val name: String = "",
    val email: String = "",
    val telegram: String = "",
    val telegramId: String = "",
    val clinic: String = "",
    val clinicPhone: String = "",
    val clinicianSharing: Boolean = false,
)

data class SolaFriend(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val telegramId: String = "",
    val telegram: String = "",
    val phone: String = "",
)

data class MoodLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val value: Int,
    val label: String,
    val quadrant: String,
    val checkIn: CheckInSession? = null,
)

/** Acceptance opens this session; only saving a mood completes it. */
data class CheckInSession(
    val id: String,
    val period: String,
    val date: String,
    val scheduledAt: Long? = null,
)

data class DiaryNote(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val text: String,
    val photo: String? = null,
    val voice: Boolean = false,
    val summary: Boolean = false,
)

data class SolaTask(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val title: String,
    val done: Boolean = false,
)

data class Butterfly(
    val id: String,
    val friend: String,
    val message: String,
    val receivedAt: Long = System.currentTimeMillis(),
)

data class CheckInWindow(val count: Int = 1, val time: String)

data class SolaPreferences(
    val cadence: String = "Every day",
    val morning: CheckInWindow = CheckInWindow(1, "10:00"),
    val afternoon: CheckInWindow = CheckInWindow(0, "14:00"),
    val evening: CheckInWindow = CheckInWindow(1, "21:00"),
    val ideaDirection: String = "balanced",
    val energyTier: String = "BIGGER",
    val includeFriends: Boolean = true,
    val showIdeas: Boolean = true,
    val selectedSources: Set<String> = emptySet(),
    val backendUrl: String = "",
)

data class SolaState(
    val onboardingComplete: Boolean = false,
    val demo: Boolean = false,
    val profile: SolaProfile = SolaProfile(),
    val interests: List<String> = listOf("Coffee walks", "Morning sun"),
    val customInterests: List<String> = emptyList(),
    val goals: List<String> = listOf("One thing I enjoy weekly", "Step outside more often"),
    val customGoals: List<String> = emptyList(),
    val completedGoals: Set<String> = emptySet(),
    val preferences: SolaPreferences = SolaPreferences(),
    val friends: List<SolaFriend> = emptyList(),
    val moods: List<MoodLog> = emptyList(),
    val diary: List<DiaryNote> = emptyList(),
    val tasks: List<SolaTask> = emptyList(),
    val butterflies: List<Butterfly> = emptyList(),
    val monthlyIntention: String = "",
    val diaryDraft: String = "",
    val draftPhoto: String? = null,
    val activeCheckIn: CheckInSession? = null,
) {
    val greeting: String
        get() = profile.name.trim().substringBefore(' ').ifBlank { "there" }

    val loggedDates: List<String>
        get() =
            (moods.map { SolaDates.key(it.timestamp) } +
                    diary.map { SolaDates.key(it.timestamp) } +
                    tasks.map { SolaDates.key(it.timestamp) })
                .distinct()
                .sorted()

    fun moodFor(date: String): MoodLog? =
        moods.filter { SolaDates.key(it.timestamp) == date }.maxByOrNull { it.timestamp }

    fun notesFor(date: String) =
        diary.filter { SolaDates.key(it.timestamp) == date }.sortedBy { it.timestamp }

    fun tasksFor(date: String) = tasks.filter { SolaDates.key(it.timestamp) == date }

    val flowerCount: Int
        get() = completedGoals.size + tasks.count { it.done }
}

data class SolaMood(
    val id: String,
    val label: String,
    val value: Int,
    val quadrant: String,
    val color: Long,
)

object SolaContent {
    val interests = listOf("Coffee walks", "Morning sun", "Quiet music", "Botanicals", "Ocean air")
    val goals =
        listOf(
            "One thing I enjoy weekly",
            "Step outside more often",
            "See friends every two weeks",
            "Make time for my hobbies",
            "Tidy my room once a week",
        )
    val moods =
        listOf(
            SolaMood("frustrated", "Frustrated", 25, "coral", 0xFFF7C4AE),
            SolaMood("anxious", "Anxious", 28, "coral", 0xFFF8CEBA),
            SolaMood("surprised", "Surprised", 82, "yellow", 0xFFFCE7A2),
            SolaMood("excited", "Excited", 96, "yellow", 0xFFFCE28D),
            SolaMood("tense", "Tense", 22, "coral", 0xFFF8CAB6),
            SolaMood("restless", "Restless", 32, "coral", 0xFFFAD4C2),
            SolaMood("hopeful", "Hopeful", 86, "yellow", 0xFFFDEAB2),
            SolaMood("happy", "Happy", 94, "yellow", 0xFFFBE498),
            SolaMood("sad", "Sad", 14, "blue", 0xFFAFC9EC),
            SolaMood("low", "Low", 18, "blue", 0xFFBBD2EF),
            SolaMood("calm", "Calm", 58, "sage", 0xFFC1E0CA),
            SolaMood("loved", "Loved", 66, "sage", 0xFFBBE0C6),
            SolaMood("drained", "Drained", 10, "blue", 0xFFB3CCE9),
            SolaMood("tired", "Tired", 24, "blue", 0xFFC2D6F0),
            SolaMood("safe", "Safe", 62, "sage", 0xFFC8E4D0),
            SolaMood("relieved", "Relieved", 68, "sage", 0xFFBFE2CA),
        )

    fun color(quadrant: String): Long =
        when (quadrant) {
            "coral" -> 0xFFEE9B86
            "yellow" -> 0xFFF6CF78
            "blue" -> 0xFF9CC0EA
            else -> 0xFFA8D4B7
        }

    fun scaleMood(level: Int) =
        when (level.coerceIn(1, 5)) {
            1 -> SolaMood("low", "Low", 0, "blue", color("blue"))
            2 -> SolaMood("tired", "Tired", 25, "blue", color("blue"))
            3 -> SolaMood("calm", "Calm", 50, "sage", color("sage"))
            4 -> SolaMood("relieved", "Relieved", 75, "sage", color("sage"))
            else -> SolaMood("happy", "Happy", 100, "yellow", color("yellow"))
        }

    fun ideas(tier: String, state: SolaState): List<Pair<String, String>> {
        val friend = state.friends.firstOrNull()?.name ?: "a friend"
        val base =
            when (tier) {
                "LIGHTER" ->
                    listOf(
                        "Sit by a sunlit window for five minutes" to "gentle & unhurried",
                        "Listen to one comforting song" to "low energy welcome",
                        "Read two pages of something gentle" to "no rush at all",
                    )
                "USUAL" ->
                    listOf(
                        "Take a slow walk around the block" to "fresh morning air",
                        "Grab a quiet coffee at your favourite spot" to "a small pocket of light",
                        "Send $friend a short voice note" to "staying lightly connected",
                    )
                else ->
                    listOf(
                        "Invite $friend for coffee this weekend" to "with a friend",
                        "Visit a park or botanical garden nearby" to "step outside for an hour",
                        "Spend an afternoon on a favourite hobby" to "making space for joy",
                    )
            }
        val ordered =
            when (state.preferences.ideaDirection) {
                "outdoors" ->
                    base.sortedByDescending {
                        it.first.contains("walk", true) ||
                            it.first.contains("park", true) ||
                            it.first.contains("sun", true)
                    }
                "social" ->
                    listOf(
                        "Send $friend a small hello" to "a little connection",
                        "Plan a slow coffee with $friend" to "something to look forward to",
                    ) + base
                "gentle-solo" ->
                    listOf(
                        "Take a quiet moment with your notebook" to "space for yourself",
                        "Make a warm drink and sit somewhere comfortable" to
                            "a little pocket of calm",
                    ) + base.filterNot { it.first.contains(friend) }
                "creative" ->
                    listOf(
                        "Sketch one small thing you can see" to "no need to make it perfect",
                        "Make a short playlist for today" to "a little room to create",
                    ) + base.reversed()
                else -> base
            }
        return if (state.preferences.includeFriends) ordered
        else
            ordered
                .map {
                    if (it.first.contains(friend))
                        "Take a quiet moment with your notebook" to "space for yourself"
                    else it
                }
                .distinct()
    }

    fun demo(now: Long = System.currentTimeMillis()): SolaState {
        val labels = listOf("Calm", "Tired", "Hopeful", "Relieved", "Happy")
        val notes =
            listOf(
                "Walked slowly under the trees and felt my breathing settle.",
                "Stayed wrapped up indoors with warm tea and gentle music.",
                "Coffee with Anna, and a slow catch-up at our corner spot.",
                "Listened to a comforting playlist before resting early.",
                "Went to the shop with my sister and walked home the long way.",
            )
        val photos = listOf("forest", "tea", "cafe", "forest", "tea")
        val times = (5 downTo 1).map { SolaDates.dayOffset(now, -it) }
        return SolaState(
            onboardingComplete = true,
            demo = true,
            profile = SolaProfile(name = "Maya", email = "maya@example.com"),
            friends = listOf(SolaFriend("demo-anna", "Anna", telegram = "@anna")),
            completedGoals = setOf(goals.first()),
            moods =
                times.mapIndexed { i, time ->
                    val mood = moods.first { it.label == labels[i] }
                    MoodLog("demo-mood-$i", time, mood.value, mood.label, mood.quadrant)
                },
            diary =
                times.mapIndexed { i, time ->
                    DiaryNote("demo-note-$i", time, notes[i], "asset:memory_${photos[i]}.jpg")
                },
        )
    }
}

object SolaDates {
    fun key(time: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(time))

    fun format(time: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))

    fun parseKey(key: String): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(key)?.time ?: 0L
    }
        .getOrDefault(0L)

    fun dayOffset(time: Long, days: Int): Long =
        Calendar.getInstance()
            .apply {
                timeInMillis = time
                add(Calendar.DAY_OF_YEAR, days)
            }
            .timeInMillis
}
