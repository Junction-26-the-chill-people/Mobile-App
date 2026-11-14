package com.example.junction.sola

import android.content.Context
import com.example.junction.sola.data.*
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SolaStoreTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clear() {
        context.getSharedPreferences("sola_local_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun clearingTheJournalRemovesItsPrivatePhotosButSavingADraftKeepsThePhoto() {
        val store = SolaStore(context)
        val relative = "sola-photos/${java.util.UUID.randomUUID()}.jpg"
        val photo =
            java.io.File(context.filesDir, relative).apply {
                parentFile?.mkdirs()
                writeText("selected photo")
            }
        store.update { it.copy(draftPhoto = relative) }
        assertTrue(store.saveDiary(false, false))
        assertTrue(photo.exists())
        store.resetCurrent()
        assertFalse(photo.exists())
    }

    @Test
    fun journalSurvivesStoreRecreationWithItsDraftAndPreferences() {
        val store = SolaStore(context)
        store.update {
            it.copy(
                onboardingComplete = true,
                profile = SolaProfile(name = "Alex"),
                diaryDraft = "A quiet walk",
                draftPhoto = "sola-photos/local.jpg",
                preferences =
                    it.preferences.copy(cadence = "Weekdays", morning = CheckInWindow(2, "10:30")),
                friends = listOf(SolaFriend("friend", "Sam", "123", "@sam", "+12345")),
            )
        }
        store.saveMood(SolaContent.moods.first { it.id == "calm" })
        assertEquals(store.state.value, SolaStore(context).state.value)
    }

    @Test
    fun sampleModeCannotOverwriteAPersonalJournal() {
        val store = SolaStore(context)
        store.update {
            it.copy(profile = SolaProfile(name = "Personal"), diaryDraft = "Private draft")
        }
        store.useSample(true)
        assertEquals("Maya", store.state.value.greeting)
        store.update { it.copy(diaryDraft = "Sample draft") }
        store.useSample(false)
        assertEquals("Personal", store.state.value.greeting)
        assertEquals("Private draft", store.state.value.diaryDraft)
        store.useSample(true)
        assertEquals("Sample draft", store.state.value.diaryDraft)
    }

    @Test
    fun severalCheckInsAndADiaryEntryInOneDayGrowOnlyOneDailyBranch() {
        val store = SolaStore(context)
        val time = SolaDates.parseKey("2026-11-14") + 12 * 60 * 60 * 1000L
        store.saveMood(SolaContent.scaleMood(2), time)
        store.saveMood(SolaContent.scaleMood(4), time + 1000)
        store.update { it.copy(diaryDraft = "Full words, not a fabricated AI summary.") }
        assertTrue(store.saveDiary(voice = true, summary = false, now = time + 2000))
        val state = store.state.value
        assertEquals(listOf("2026-11-14"), state.loggedDates)
        assertEquals(2, state.moods.size)
        assertEquals("Relieved", state.moodFor("2026-11-14")?.label)
        assertEquals("Full words, not a fabricated AI summary.", state.diary.single().text)
        assertEquals("", state.diaryDraft)
        assertNull(state.draftPhoto)
        assertTrue(state.diary.single().voice)
    }

    @Test
    fun anEmptyDiaryIsNotSavedButAPhotoOnlyMemoryIs() {
        val store = SolaStore(context)
        assertFalse(store.saveDiary(false, false))
        store.update { it.copy(draftPhoto = "sola-photos/photo.jpg") }
        assertTrue(store.saveDiary(false, false))
        assertEquals("sola-photos/photo.jpg", store.state.value.diary.single().photo)
    }

    @Test
    fun resettingSampleDoesNotClearThePersonalJournal() {
        val store = SolaStore(context)
        store.update { it.copy(profile = SolaProfile(name = "Owner")) }
        store.useSample(true)
        store.resetCurrent()
        store.useSample(false)
        assertEquals("Owner", store.state.value.profile.name)
    }

    @Test
    fun debugScreenPreviewsNeverPersistSampleOrPersonalChanges() {
        val personal = SolaStore(context)
        personal.update { it.copy(profile = SolaProfile(name = "Owner")) }
        val preview = SolaStore(context, SolaContent.demo())
        preview.update { it.copy(profile = SolaProfile(name = "Preview")) }
        preview.useSample(false)
        preview.update { it.copy(profile = SolaProfile(name = "Ephemeral")) }
        assertEquals("Owner", SolaStore(context).state.value.profile.name)
    }

    @Test
    fun versionedJsonRoundTripsAllFeatureDataAndRejectsAnUnknownVersion() {
        val state =
            SolaContent.demo()
                .copy(
                    butterflies = listOf(Butterfly("b", "A", "Hello", 100)),
                    monthlyIntention = "More fresh air",
                    customGoals = listOf("Custom"),
                    customInterests = listOf("Art"),
                    tasks = listOf(SolaTask("t", 100, "A little win", true)),
                )
        assertEquals(state, SolaJson.decode(SolaJson.encode(state)))
        assertThrows(IllegalArgumentException::class.java) { SolaJson.decode("{\"version\":2}") }
    }

    @Test
    fun localDayKeysFollowThePhoneTimeZoneIncludingAcrossMidnight() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Helsinki"))
            assertEquals("2026-11-15", SolaDates.key(1794697200000L))
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun productionBridgeRequiresHttpsAndDebugHttpIsLimitedToLocalDevelopment() {
        assertTrue(TelegramGateway.validBase("https://bridge.example", false))
        assertFalse(TelegramGateway.validBase("http://bridge.example", true))
        assertTrue(TelegramGateway.validBase("http://127.0.0.1:3001", true))
        assertFalse(TelegramGateway.validBase("http://127.0.0.1:3001", false))
        assertFalse(TelegramGateway.validBase("https://user:secret@bridge.example", false))
    }
}
