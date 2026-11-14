package com.junction.catnudge

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.junction.catnudge.visual.NudgeOverlayView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

/** Renders the actual Android views for visual QA, not a separate browser recreation. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NudgePreviewTest {
    @Before fun enableAnimations() { AndroidAnimationClock.setScale(1f) }
    @After fun restoreAnimations() { AndroidAnimationClock.setScale(1f) }
    @Test fun portraitPreview() = renderVisit(390, 844, "portrait")

    @Test @Config(qualifiers = "w844dp-h390dp-land-mdpi")
    fun landscapePreview() = renderVisit(844, 390, "landscape")

    @Test @Config(qualifiers = "w320dp-h568dp-port-mdpi")
    fun smallPhonePreview() = renderVisit(320, 568, "small-phone")

    @Test fun largeFontPreview() {
        RuntimeEnvironment.setFontScale(2f)
        try { renderVisit(390, 844, "large-font") }
        finally { RuntimeEnvironment.setFontScale(1f) }
    }

    @Test fun animationPreview() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        var dismissals = 0
        val view = NudgeOverlayView(activity.get(), {}, { dismissals++ })
        try {
            activity.get().setContentView(view)
            layout(view, 390, 844)
            AndroidAnimationClock.setScale(1f)
            view.start()
            idle(16)
            repeat(84) { frame ->
                if (frame == 38 || frame == 51 || frame == 70) view.performClick()
                if (frame == 10 || frame == 23) assertEquals("ARRIVING", view.visitState)
                if (frame == 32) assertEquals("READY", view.visitState)
                if (frame == 62) assertEquals("FAREWELL", view.visitState)
                layout(view, 390, 844)
                save(view, 390, 844, "animation/frame-${frame.toString().padStart(3, '0')}")
                idle(100)
            }
            assertEquals("FINISHED", view.visitState)
            assertEquals(1, dismissals)
        } finally {
            view.dispose()
            activity.pause().stop().destroy()
        }
    }

    private fun renderVisit(width: Int, height: Int, name: String) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val view = NudgeOverlayView(activity.get(), {}, {})
        try {
            activity.get().setContentView(view)
            layout(view, width, height)
            AndroidAnimationClock.setScale(1f)
            view.start()
            idle(1200)
            assertEquals("ARRIVING", view.visitState)
            layout(view, width, height)
            save(view, width, height, "$name-arriving")
            idle(2300)
            layout(view, width, height)
            assertEquals("READY", view.visitState)
            assertTrue(view.cardView.width <= width)
            assertTrue(view.cardView.height < height)
            val buttonRect = android.graphics.Rect()
            assertTrue("Yes button should remain visible", view.yesButton.getGlobalVisibleRect(buttonRect))
            assertTrue("Yes button should fit on screen", buttonRect.top >= 0 && buttonRect.bottom <= height)
            assertTrue("Question should not be ellipsized", (0 until view.messageView.lineCount).all {
                view.messageView.layout.getEllipsisCount(it) == 0
            })
            assertTrue("Question should fit its measured text area",
                view.messageView.layout.getLineBottom(view.messageView.lineCount - 1) <=
                    view.messageView.height - view.messageView.compoundPaddingTop - view.messageView.compoundPaddingBottom)
            save(view, width, height, "$name-question")
            view.performClick()
            idle(850)
            layout(view, width, height)
            save(view, width, height, "$name-please")
            view.performClick()
            idle(850)
            layout(view, width, height)
            assertEquals("FAREWELL", view.visitState)
            idle(10_000)
            assertEquals("FAREWELL", view.visitState)
            assertEquals("fine :(", view.messageView.text.toString())
            assertEquals(1f, (view.cardView.parent as View).alpha, 0f)
            save(view, width, height, "$name-goodbye")
        } finally {
            view.dispose()
            activity.pause().stop().destroy()
        }
    }

    private fun save(view: View, width: Int, height: Int, name: String) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // A subdued example app makes the transparent overlay legible in a standalone image.
        canvas.drawColor(Color.rgb(235, 240, 241))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(49, 68, 70)
        paint.textSize = 27f
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        canvas.drawText("Today", 28f, 65f, paint)
        paint.textSize = 12f
        paint.color = Color.rgb(115, 132, 134)
        canvas.drawText("An example app behind the cat", 28f, 90f, paint)
        paint.color = Color.rgb(250, 252, 252)
        repeat(if (width > height) 1 else 3) { index ->
            val top = 119f + index * 125
            canvas.drawRoundRect(24f, top, width - 24f, top + 104, 15f, 15f, paint)
        }
        view.draw(canvas)
        val directory = File("build/preview").apply { mkdirs() }
        val destination = File(directory, "$name.png")
        destination.parentFile?.mkdirs()
        destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    private fun idle(ms: Long) = AndroidAnimationClock.advance(ms)
}
