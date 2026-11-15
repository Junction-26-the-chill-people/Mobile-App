package com.example.junction.sola

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.junction.sola.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SolaMotionTest {
    @Test
    fun sourceAmbientAndWingTimingsLoopAtTheOriginalPeriods() {
        assertEquals(0f, SolaMotion.cycle(0f, 7f), .001f)
        assertEquals(1f, SolaMotion.cycle(3.5f, 7f), .001f)
        assertEquals(0f, SolaMotion.cycle(7f, 7f), .001f)
        assertEquals(1f, SolaMotion.wing(0f), .001f)
        assertEquals(.42f, SolaMotion.wing(.325f), .001f)
        assertEquals(1f, SolaMotion.wing(.65f), .001f)
    }

    @Test
    fun trunkAndBranchesArriveInOrderWhileExistingMemoriesStayGrown() {
        val first = GardenGrowthPlan(0, 5)
        assertEquals(0f, first.trunk(.239f), .001f)
        assertEquals(1f, first.trunk(.9f), .001f)
        assertEquals(0f, first.branch(0, .51f), .001f)
        assertTrue(first.branch(0, .8f) > 0f)
        assertEquals(0f, first.branch(2, .8f), .001f)
        assertTrue(first.branch(0, .9f) > first.branch(1, .9f))
        val next = GardenGrowthPlan(5, 6)
        assertEquals(1f, next.branch(0, 0f), .001f)
        assertEquals(1f, next.leaf(4, 0f), .001f)
        assertEquals(0f, next.leaf(5, .1f), .001f)
        assertTrue(next.leaf(5, .4f) > 0f)
        assertEquals(1f, next.leaf(5, 1f), .001f)
        assertEquals(0f, GardenGrowthPlan(5, 5).duration, .001f)
    }

    @Test
    fun budsAndLeavesHaveASmallBloomOvershootThenSettle() {
        assertEquals(0f, SolaMotion.bloom(-1f), .001f)
        assertTrue(SolaMotion.bloom(.68f) > 1f)
        assertEquals(1f, SolaMotion.bloom(1f), .001f)
        assertEquals(1f, SolaMotion.bloom(10f), .001f)
        val plan = GardenGrowthPlan(0, 1)
        assertEquals(0f, plan.bud(0, 0, .8f), .001f)
        assertTrue(plan.bud(0, 0, 1f) > plan.bud(0, 2, 1f))
    }

    @Test
    fun nativeButterflyFramesShowTheOriginalWingsOpeningAndClosing() {
        val directory = File("build/sola-preview/butterfly-motion").apply { mkdirs() }
        val frames =
            listOf(0f, .1625f, .325f).mapIndexed { index, seconds ->
                val bitmap = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
                CanvasDrawScope().draw(
                    Density(1f),
                    LayoutDirection.Ltr,
                    Canvas(android.graphics.Canvas(bitmap)),
                    Size(144f, 144f),
                ) {
                    drawButterfly(seconds)
                }
                File(directory, "wing-$index.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                bitmap.recycle()
                pixels.count { (it ushr 24) > 10 }
            }
        assertTrue(frames[0] > frames[1])
        assertTrue(frames[1] > frames[2])
    }
}
