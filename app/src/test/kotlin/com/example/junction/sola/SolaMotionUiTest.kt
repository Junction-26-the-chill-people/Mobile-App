package com.example.junction.sola

import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.junction.sola.data.*
import com.example.junction.sola.ui.*
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
class SolaMotionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun nativeGardenGrowthWorksAtZeroAnimatorScaleAndDoesNotRestartOnReturn() {
        // Robolectric's isolated settings only; never change a physical phone's animation settings.
        val context = RuntimeEnvironment.getApplication()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        val middleOfMonth = SolaDates.parseKey(SolaDates.key().take(7) + "-15")
        val initial =
            SolaContent.demo()
                .copy(
                    moods =
                        List(5) { index ->
                            MoodLog(
                                timestamp = SolaDates.dayOffset(middleOfMonth, -index),
                                value = 58,
                                label = "Calm",
                                quadrant = "sage",
                            )
                        },
                    diary = emptyList(),
                    butterflies =
                        listOf(Butterfly("motion-demo", "Demo friend", "A little hello.")),
                )
        val store = SolaStore(context, initial)
        lateinit var view: View
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            SolaApp(store, SolaScreen.GARDEN, motion = true)
        }
        compose.mainClock.advanceTimeBy(400)
        val sprout = frame(view, "garden-0")
        compose.mainClock.advanceTimeBy(800)
        frame(view, "garden-1")
        compose.mainClock.advanceTimeBy(2400)
        val grown = frame(view, "garden-2")
        assertTrue(
            "The native tree should visibly grow across frames",
            wood(grown) > wood(sprout) * 1.5f,
        )
        compose.onNodeWithText("Replay growth").performClick()
        compose.mainClock.advanceTimeBy(400)
        val replayed = frame(view, "garden-replay")
        assertTrue(
            "Replay should explicitly restart the growth",
            wood(replayed) < wood(grown) * .7f,
        )
        compose.mainClock.advanceTimeBy(3200)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.mainClock.advanceTimeBy(400)
        val returned = frame(view, "garden-return")
        assertTrue(
            "Returning to the garden must keep the existing tree",
            wood(returned) >= wood(grown) * .8f,
        )
        compose.onNodeWithContentDescription("Butterfly from Demo friend").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText("A little hello.").assertExists()
        compose.runOnIdle { assertEquals(initial, store.state.value) }
    }

    @Test
    fun ambientTimePausesWhenTheScreenIsNotResumed() {
        class Owner : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle
                get() = registry
        }
        val owner = Owner()
        lateinit var clock: State<Float>
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides owner,
                LocalSceneMotion provides true,
            ) {
                clock = sceneClock()
            }
        }
        compose.mainClock.advanceTimeBy(600)
        val beforePause = clock.value
        assertTrue(beforePause > .3f)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.mainClock.advanceTimeBy(3000)
        assertEquals(beforePause, clock.value, .001f)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeBy(600)
        assertTrue(clock.value - beforePause in 0.4f..0.7f)
    }

    @Test
    fun rapidMoodChangesSaveTheLatestChoiceWhileSelectionAnimationsAreStillRunning() {
        val store =
            SolaStore(RuntimeEnvironment.getApplication(), SolaState(onboardingComplete = true))
        compose.mainClock.autoAdvance = false
        compose.setContent { SolaApp(store, SolaScreen.CHECK_IN, motion = true) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Happy mood").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithContentDescription("Anxious mood").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithContentDescription("Calm mood").performClick()
        compose.onNodeWithText("Rest here").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Your garden").assertExists()
        compose.runOnIdle { assertEquals("Calm", store.state.value.moods.single().label) }
    }

    private fun frame(view: View, name: String): Bitmap {
        compose.waitForIdle()
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/sola-preview/restored-motion/$name.png")
                .apply { parentFile?.mkdirs() }
                .outputStream()
                .use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
        }
        return bitmap
    }

    private fun wood(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * 250)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 200, bitmap.width, 250)
        return pixels.count { pixel ->
            val r = (pixel shr 16) and 255
            val g = (pixel shr 8) and 255
            val b = pixel and 255
            r in 112..175 && g in 85..145 && b in 65..130 && r > g && g > b
        }
    }
}
