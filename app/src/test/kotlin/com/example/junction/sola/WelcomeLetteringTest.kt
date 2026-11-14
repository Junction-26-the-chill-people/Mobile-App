package com.example.junction.sola

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.junction.sola.ui.HelloLettering
import com.example.junction.sola.ui.WelcomeTiming
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WelcomeLetteringTest {
    @Test
    fun theNativePenProgressivelyWritesTheOriginalVectorAndRendersAPreview() {
        val lettering = HelloLettering(RuntimeEnvironment.getApplication())
        val output = File("build/sola-preview/hello-animation").apply { mkdirs() }
        val pixelsByFrame =
            listOf(0f, .1f, .35f, 1f).mapIndexed { index, progress ->
                val bitmap = Bitmap.createBitmap(692, 176, Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(
                    Density(1f),
                    LayoutDirection.Ltr,
                    Canvas(android.graphics.Canvas(bitmap)),
                    Size(692f, 176f),
                ) {
                    lettering.draw(this, progress)
                }
                File(output, "stroke-$index.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                bitmap.recycle()
                pixels.count { (it ushr 24) > 4 }
            }
        assertEquals(0, pixelsByFrame.first())
        assertTrue(pixelsByFrame[1] > 0)
        assertTrue(pixelsByFrame[1] < pixelsByFrame[2])
        assertTrue(pixelsByFrame[2] < pixelsByFrame[3])
    }

    @Test
    fun theReferenceTimingWritesForTwoPointTwoSecondsThenKeepsTheGreetingVisible() {
        assertEquals(0f, WelcomeTiming.ink(0), .001f)
        assertTrue(WelcomeTiming.ink(600) in .1f..1f)
        assertEquals(1f, WelcomeTiming.ink(2200), .001f)
        assertEquals(1f, WelcomeTiming.ink(10000), .001f)
        assertEquals(0f, WelcomeTiming.subtitle(1299), .001f)
        assertTrue(WelcomeTiming.subtitle(1600) > 0f)
        assertEquals(1f, WelcomeTiming.subtitle(2200), .001f)
    }
}
