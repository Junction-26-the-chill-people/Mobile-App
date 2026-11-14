package com.junction.catnudge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.junction.catnudge.visual.CatSceneView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Checks the physical contact contract of the paw positions actually consumed by the renderer. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatGaitTest {
    @Test fun plantedPawsStayOnTheGroundWhileEveryLegTakesStepsInBothDirections() {
        val cat = CatSceneView(RuntimeEnvironment.getApplication()).apply { walkAmount = 1f }
        cat.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY))
        cat.layout(0, 0, 300, 160)
        val bitmap = Bitmap.createBitmap(300, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        try {
            for (direction in listOf(1f, -1f)) {
                var previousDistance = 0f
                var previous = emptyMap<String, CatSceneView.FootPose>()
                var plantedComparisons = 0
                val legsThatPlanted = mutableSetOf<String>()
                val legsThatLifted = mutableSetOf<String>()
                for (travel in 0..220 step 2) {
                    val distance = direction * travel
                    cat.walkDistance = distance
                    bitmap.eraseColor(Color.TRANSPARENT)
                    cat.draw(canvas)
                    val feet = cat.renderedFootPoses
                    assertEquals("The drawing should account for all four paws", 4, feet.size)
                    assertTrue("The walking cat should always have ground support", feet.any { it.planted })
                    for (foot in feet) {
                        if (foot.planted) {
                            legsThatPlanted.add(foot.leg)
                            assertEquals("A planted paw should meet the ground", foot.groundY, foot.y, .001f)
                            val lastFoot = previous[foot.leg]
                            if (lastFoot?.planted == true) {
                                // Parent travel plus local paw position is the position on the phone.
                                assertEquals("${foot.leg} should not skate while planted",
                                    previousDistance + lastFoot.x, distance + foot.x, .01f)
                                plantedComparisons++
                            }
                        } else if (foot.y < foot.groundY - .1f) {
                            legsThatLifted.add(foot.leg)
                        }
                    }
                    previous = feet.associateBy { it.leg }
                    previousDistance = distance
                }
                assertTrue("Check sustained ground contact rather than isolated poses", plantedComparisons > 150)
                assertEquals("Each leg should carry weight", 4, legsThatPlanted.size)
                assertEquals("Each leg should lift to take a step", legsThatPlanted, legsThatLifted)
            }
        } finally {
            cat.stop()
            bitmap.recycle()
        }
    }
}
