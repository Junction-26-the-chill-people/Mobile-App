package com.example.junction.ui.main

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.junction.MainActivity
import org.junit.Rule
import org.junit.Test

/** Smoke tests for the app's actual launcher and future-question destination. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun launcherOffersANudge() {
    composeTestRule.onNodeWithText("Small paws.\nA little pause.").assertExists()
    composeTestRule.onNodeWithText("Stop visit").assertExists()
  }

  @Test
  fun futureSpaceOpensAndReturnsToTheLauncher() {
    composeTestRule.onNodeWithText("Future space  ↗").performClick()
    composeTestRule.onNodeWithText("<reserved for future>").assertExists()
    composeTestRule.onNodeWithText("← Back").performClick()
    composeTestRule.onNodeWithText("Small paws.\nA little pause.").assertExists()
  }
}
