package com.example.junction.sola

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.junction.sola.data.*
import java.io.File
import org.junit.Assert.assertEquals
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
class BasicRecordUiTest {
    @get:Rule val compose = createComposeRule()
    private val initial =
        SolaState(
            onboardingComplete = true,
            moods =
                listOf(
                    MoodLog(value = 94, label = "Happy", quadrant = "yellow"),
                    MoodLog(value = 58, label = "Calm", quadrant = "sage"),
                    MoodLog(value = 58, label = "Calm", quadrant = "sage"),
                ),
        )

    @Test
    fun theQuietSettingsLinkOpensPersonalCountsAndReturnsToSettings() {
        val store = SolaStore(RuntimeEnvironment.getApplication(), initial)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            SolaApp(store, SolaScreen.SETTINGS, motion = false)
        }
        compose
            .onNodeWithText("Your record")
            .assertIsNotDisplayed()
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Your record").assertExists()
        compose.onNodeWithText("2 times").assertExists()
        compose.onNodeWithText("1 time").assertExists()
        screenshot(view, "basic-record-personal")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Make yourself at home.").assertExists()
        compose.runOnIdle { assertEquals(initial, store.state.value) }
    }

    @Test
    fun choosingAFictionalPersonShowsTheirOwnBasicLogsAndWebsiteNoticeWithoutEditingTheJournal() {
        val store = SolaStore(RuntimeEnvironment.getApplication(), initial)
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            SolaApp(store, SolaScreen.BASIC_LOGS, motion = false)
        }
        compose.onNodeWithText("My record · choose a view").performClick()
        compose.onNodeWithText("Noah").performScrollTo().performClick()
        compose.onNodeWithText("Noah’s record").assertExists()
        compose.onNodeWithText("Fictional sample data · read only").assertExists()
        compose.onNodeWithText("7 days").performClick()
        screenshot(view, "basic-record-noah")
        compose.onNodeWithText("More on the website").performScrollTo().performClick()
        compose.onNodeWithText("Noah · demo profile", ignoreCase = true).assertExists()
        compose.onNodeWithText("The website is not available on phones yet.").assertExists()
        compose.onNodeWithText("Back to these logs").performScrollTo().performClick()
        compose.onNodeWithText("Noah · demo").performScrollTo().performClick()
        compose.onNodeWithText("Mila").performScrollTo().performClick()
        compose.onNodeWithText("Mila’s record").assertExists()
        compose.onNodeWithText("Mila · demo").performScrollTo().performClick()
        compose.onNodeWithText("My record").performScrollTo().performClick()
        compose.onNodeWithText("Your record").assertExists()
        compose.onNodeWithText("2 times").assertExists()
        compose.runOnIdle { assertEquals(initial, store.state.value) }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun basicLogsAndTheirWebsiteNoticeRemainReachableWithLargeText() {
        RuntimeEnvironment.setFontScale(2f)
        val store = SolaStore(RuntimeEnvironment.getApplication(), initial)
        compose.setContent { SolaApp(store, SolaScreen.BASIC_LOGS, motion = false) }
        compose.onNodeWithText("More on the website").performScrollTo().performClick()
        compose.onNodeWithText("The website is not available on phones yet.").assertExists()
        compose.onNodeWithText("Back to these logs").performScrollTo().performClick()
        compose.onNodeWithText("My record · choose a view").performScrollTo().performClick()
        compose.onNodeWithText("Mila").performScrollTo().performClick()
        compose.onNodeWithText("Mila’s record").assertExists()
    }

    private fun screenshot(view: View, name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/sola-preview/$name.png")
                .apply { parentFile?.mkdirs() }
                .outputStream()
                .use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            bitmap.recycle()
        }
    }
}
