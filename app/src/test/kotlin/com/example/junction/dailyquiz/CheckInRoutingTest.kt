package com.example.junction.dailyquiz

import android.content.Context
import android.content.Intent
import com.example.junction.MainActivity
import com.example.junction.nudge.NudgeController
import com.example.junction.sola.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35])
class CheckInRoutingTest {
    @Test
    fun anAlreadyOpenJournalSavesTheSessionReceivedThroughAnotherEntryPoint() {
        val openJournal = SolaStore(context)
        val incoming = SolaStore(context)
        val visit = CheckInSession("warm-intent", "morning", SolaDates.key())
        incoming.openCheckIn(visit)
        openJournal.saveMood(SolaContent.scaleMood(3))
        val saved = SolaStore.personal(context)
        assertEquals(visit, saved.moods.single().checkIn)
        assertNull(saved.activeCheckIn)
        assertFalse(incoming.openCheckIn(visit))
    }

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clear() {
        for (name in listOf(SolaStore.PREFS, "catnudge_state", "daily_quiz_schedule")) context
            .getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun launch(id: String) =
        Intent(context, MainActivity::class.java)
            .setAction(NudgeController.ACTION_OPEN_QUESTIONS)
            .putExtra("nudge_id", id)
            .putExtra("period", "evening")
            .putExtra("date", "2026-11-14")
            .putExtra("scheduled_at", "123456")
            .putExtra("preview_motion", false)

    @Test
    fun coldAndWarmWidgetIntentsOpenTheRealPersonalCheckIn() {
        val controller =
            Robolectric.buildActivity(
                    MainActivity::class.java,
                    launch("daily-quiz:2026-11-14:evening"),
                )
                .setup()
                .visible()
        try {
            assertEquals(
                "daily-quiz:2026-11-14:evening",
                SolaStore.personal(context).activeCheckIn?.id,
            )
            controller.newIntent(launch("second-visit"))
            assertEquals("second-visit", SolaStore.personal(context).activeCheckIn?.id)
            assertEquals("evening", SolaStore.personal(context).activeCheckIn?.period)
            assertTrue(SolaStore.personal(context).moods.isEmpty())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun acceptingIsDurableButOnlySavingCompletesTheSession() {
        val session = CheckInRoute.fromIntent(launch("daily-quiz:2026-11-14:evening"))
        val store = SolaStore(context)
        assertTrue(store.openCheckIn(session))
        assertTrue(SolaStore(context).state.value.moods.isEmpty())
        assertEquals(session, SolaStore(context).state.value.activeCheckIn)
        val restored = SolaStore(context)
        restored.saveMood(SolaContent.scaleMood(4))
        assertNull(restored.state.value.activeCheckIn)
        assertEquals(session, restored.state.value.moods.single().checkIn)
        assertFalse(restored.openCheckIn(session))
        assertEquals(1, restored.state.value.moods.size)
    }

    @Test
    fun aRealNudgeNeverWritesIntoTheSampleGarden() {
        val store = SolaStore(context)
        store.update { it.copy(profile = SolaProfile(name = "Owner")) }
        store.useSample(true)
        store.update { it.copy(diaryDraft = "Sample-only draft") }
        val sample = store.state.value
        store.openCheckIn(CheckInSession("real", "morning", SolaDates.key()))
        store.saveMood(SolaContent.scaleMood(3))
        assertEquals("Owner", store.state.value.profile.name)
        store.useSample(true)
        assertEquals(sample, store.state.value)
    }

    @Test
    fun oldJournalJsonStillLoadsWithoutSessionFields() {
        val original =
            SolaState(moods = listOf(MoodLog(value = 50, label = "Calm", quadrant = "sage")))
        assertEquals(original, SolaJson.decode(SolaJson.encode(original)))
        assertNull(SolaJson.decode("{\"version\":1}").activeCheckIn)
    }
}
