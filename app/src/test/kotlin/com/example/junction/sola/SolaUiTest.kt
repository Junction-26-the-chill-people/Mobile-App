package com.example.junction.sola

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.junction.sola.data.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SolaUiTest {
    @get:Rule val compose = createComposeRule()

    private fun store(state: SolaState = SolaState()) =
        SolaStore(RuntimeEnvironment.getApplication(), state)

    @Test
    @Config(qualifiers = "w640dp-h320dp-land-mdpi")
    fun theWelcomeActionIsReachableInLandscapeWithLargeText() {
        RuntimeEnvironment.setFontScale(1.5f)
        compose.setContent { SolaApp(store(), SolaScreen.WELCOME, motion = false) }
        compose.onNodeWithText("hello").assertExists()
        compose.onNodeWithText("Begin").performScrollTo().performClick()
        compose.onNodeWithText("Come on in.").assertExists()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun theWelcomeActionIsReachableAtTwiceTheNormalFontSize() {
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent { SolaApp(store(), SolaScreen.WELCOME, motion = false) }
        compose.onNodeWithText("hello").assertExists()
        compose.onNodeWithText("Begin").performScrollTo().performClick()
        compose.onNodeWithText("Come on in.").assertExists()
    }

    @Test
    fun aVisitArrivingWhileTodayIsOpenRoutesAndSavesItsSession() {
        val journal = store(SolaState(onboardingComplete = true))
        val visit = CheckInSession("warm-ui-visit", "evening", SolaDates.key())
        compose.setContent { SolaApp(journal, SolaScreen.TODAY, motion = false) }
        compose.runOnIdle { journal.openCheckIn(visit) }
        compose.onNodeWithText("How are you,\nright now?").assertExists()
        compose.onNodeWithText("Rest here").performScrollTo().performClick()
        compose.onNodeWithText("Your garden").assertExists()
        compose.runOnIdle {
            assertEquals(visit, journal.state.value.moods.single().checkIn)
            assertNull(journal.state.value.activeCheckIn)
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun theCheckInRemainsReachableOnASmallPhoneWithLargeText() {
        RuntimeEnvironment.setFontScale(1.3f)
        val store = store(SolaState(onboardingComplete = true))
        compose.setContent { SolaApp(store, SolaScreen.TODAY, motion = false) }
        compose.onNodeWithContentDescription("Check in. How are you?").performClick()
        compose.onNodeWithText("Rest here").performScrollTo().performClick()
        compose.onNodeWithText("Your garden").assertExists()
    }

    @Test
    fun settingsReturnsToTheCheckInAndBackThenReturnsToToday() {
        compose.setContent {
            SolaApp(store(SolaState(onboardingComplete = true)), SolaScreen.TODAY, motion = false)
        }
        compose.onNodeWithContentDescription("Check in. How are you?").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Make yourself at home.").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("How are you,\nright now?").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Welcome back,").assertExists()
    }

    @Test
    fun checkInRecordsARealMoodAndOpensItsGardenMemory() {
        val store = store(SolaState(onboardingComplete = true))
        compose.setContent { SolaApp(store, SolaScreen.TODAY, motion = false) }
        compose.onNodeWithContentDescription("Check in. How are you?").performClick()
        compose.onNodeWithContentDescription("Happy mood").performClick()
        compose.onNodeWithText("Rest here").performScrollTo().performClick()
        compose.onNodeWithText("Your garden").assertExists()
        compose.runOnIdle {
            assertEquals("Happy", store.state.value.moods.single().label)
            assertEquals(1, store.state.value.loggedDates.size)
        }
        compose.onNodeWithContentDescription("Memory for ${SolaDates.key()}, Happy").performClick()
        compose.onNodeWithText("Happy ·", substring = true).assertExists()
    }

    @Test
    fun diarySaveKeepsTheFullTextClearsTheDraftAndShowsItOnTheTree() {
        val store = store(SolaState(onboardingComplete = true))
        compose.setContent { SolaApp(store, SolaScreen.DIARY, motion = false) }
        compose
            .onNodeWithText("What’s on your mind?")
            .performTextInput("A walk in the afternoon light.")
        compose.onNodeWithText("Add to today").performScrollTo().performClick()
        compose.onNodeWithText("Your garden").assertExists()
        compose.runOnIdle {
            assertEquals("A walk in the afternoon light.", store.state.value.diary.single().text)
            assertEquals("", store.state.value.diaryDraft)
        }
    }

    @Test
    fun ideaSelectionCreatesATaskRatherThanPretendingItIsCompleted() {
        val store = store(SolaState(onboardingComplete = true))
        compose.setContent { SolaApp(store, SolaScreen.TODAY, motion = false) }
        compose.onNodeWithContentDescription("Find an idea").performClick()
        compose.onNodeWithText("LIGHTER").performClick()
        compose.onNodeWithText("Let’s go").performClick()
        compose.runOnIdle {
            assertEquals(1, store.state.value.tasks.size)
            assertFalse(store.state.value.tasks.single().done)
            assertEquals("LIGHTER", store.state.value.preferences.energyTier)
        }
    }

    @Test
    fun allFifteenNativeScreensRenderAndProduceReviewablePreviews() {
        val initial = mutableStateOf(SolaScreen.WELCOME)
        val store = store(SolaContent.demo())
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            key(initial.value) { SolaApp(store, initial.value, motion = false) }
        }
        val directory = File("build/sola-preview").apply { mkdirs() }
        SolaScreen.entries.forEach { screen ->
            compose.runOnIdle { initial.value = screen }
            compose.waitForIdle()
            lateinit var image: Bitmap
            compose.runOnIdle {
                image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(image))
            }
            File(directory, "${screen.name.lowercase()}.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            assertTrue(
                "$screen should have a phone-sized composition",
                image.width >= 300 && image.height >= 600,
            )
        }
    }
}
