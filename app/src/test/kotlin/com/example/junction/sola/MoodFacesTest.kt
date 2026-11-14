package com.example.junction.sola

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.junction.sola.data.SolaContent
import com.example.junction.sola.ui.drawMoodFace
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoodFacesTest {
    @Test
    fun allSixteenExpressionsAreDistinctEvenWithTheSameBackgroundColour() {
        val sheet = Bitmap.createBitmap(640, 720, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        canvas.drawColor(0xFFFAF6F0.toInt())
        val labels =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF5B584C.toInt()
                textSize = 18f
                textAlign = Paint.Align.CENTER
            }
        val shapes =
            SolaContent.moods.mapIndexed { index, mood ->
                fun render(neutral: Boolean): Bitmap {
                    val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
                    CanvasDrawScope().draw(
                        Density(1f),
                        LayoutDirection.Ltr,
                        Canvas(android.graphics.Canvas(bitmap)),
                        Size(120f, 120f),
                    ) {
                        drawMoodFace(if (neutral) mood.copy(color = 0xFFE1DDD4) else mood)
                    }
                    return bitmap
                }
                val face = render(false)
                canvas.drawBitmap(face, (index % 4) * 160f + 20, (index / 4) * 180f + 15, null)
                canvas.drawText(
                    mood.label,
                    (index % 4) * 160f + 80,
                    (index / 4) * 180f + 160,
                    labels,
                )
                val neutral = render(true)
                val pixels = IntArray(neutral.width * neutral.height)
                neutral.getPixels(pixels, 0, neutral.width, 0, 0, neutral.width, neutral.height)
                face.recycle()
                neutral.recycle()
                pixels.toList()
            }
        assertEquals(
            "Each label needs its own expression, independent of colour",
            16,
            shapes.distinct().size,
        )
        File("build/sola-preview/mood-faces.png")
            .apply { parentFile?.mkdirs() }
            .outputStream()
            .use {
                sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        sheet.recycle()
    }
}
