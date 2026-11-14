package com.example.junction.ui.main

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.junction.MainActivity
import com.example.junction.nudge.NudgeController
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Actual Activity routes; preview records are ephemeral and cannot modify a personal journal. */
class MainScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun launch(screen: String) {
        scenario =
            ActivityScenario.launch(
                Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                    .putExtra("preview_screen", screen)
                    .putExtra("preview_motion", false)
            )
    }

    @After
    fun close() {
        scenario?.close()
    }

    @Test
    fun launcherShowsTheNativeWelcomeAndCanEnterToday() {
        launch("welcome")
        compose.onNodeWithText("hello").assertExists()
        compose.onNodeWithText("Skip to home").performClick()
        compose.onNodeWithText("Welcome back,").assertExists()
        compose.onNodeWithContentDescription("Check in. How are you?").assertExists()
    }

    @Test
    fun aNativeCheckInOpensTheGardenAndTheOtherTabs() {
        launch("today")
        compose.onNodeWithContentDescription("Check in. How are you?").performClick()
        compose.onNodeWithContentDescription("Happy mood").performClick()
        compose.onNodeWithText("Rest here").performScrollTo().performClick()
        compose.onNodeWithText("Your garden").assertExists()
        compose.onNodeWithContentDescription("Statistics", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Gentle patterns over time").assertExists()
        compose.onNodeWithContentDescription("Support", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Always close.").assertExists()
    }

    @Test
    fun catDestinationOpensTheNativeCheckInAndSavesToTheGarden() {
        scenario =
            ActivityScenario.launch(
                Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                    .setAction(NudgeController.ACTION_OPEN_QUESTIONS)
                    .putExtra("preview_screen", "check_in")
                    .putExtra("preview_demo", false)
                    .putExtra("preview_motion", false)
                    .putExtra("nudge_id", "device-integration-test")
            )
        compose.onNodeWithText("How are you,\nright now?").assertExists()
        compose.onNodeWithContentDescription("Calm mood").performClick()
        compose.onNodeWithText("Rest here").performScrollTo().performClick()
        compose.onNodeWithText("Your garden").assertExists()
    }
}
