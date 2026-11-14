package com.example.junction.sola.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.example.junction.sola.data.SolaMood

@Composable
fun MoodFace(mood: SolaMood, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawMoodFace(mood) }
}

/** Sixteen label-specific expressions; their shapes remain distinct even without their colours. */
internal fun DrawScope.drawMoodFace(mood: SolaMood) {
    val tint = Color(mood.color)
    val radius = size.minDimension / 2
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(.65f), tint),
            center - Offset(radius * .3f, radius * .35f),
            radius * 1.8f,
        ),
        radius,
    )
    drawCircle(Color.White.copy(.7f), radius - 1, style = Stroke(1f))
    scale(size.width / 64f, size.height / 64f, Offset.Zero) {
        val ink = Color(0xFF5B584C).copy(.85f)
        val pen = Stroke(1.35f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float, color: Color = ink) =
            drawLine(color, Offset(x, y), Offset(x2, y2), 1.35f, StrokeCap.Round)
        fun curve(
            x: Float,
            y: Float,
            cx: Float,
            cy: Float,
            x2: Float,
            y2: Float,
            color: Color = ink,
        ) {
            val path =
                Path().apply {
                    moveTo(x, y)
                    quadraticTo(cx, cy, x2, y2)
                }
            drawPath(path, color, style = pen)
        }
        fun dots(y: Float = 28f, lookX: Float = 0f, lookY: Float = 0f, r: Float = 1.45f) {
            listOf(25f, 39f).forEach { x -> drawCircle(ink, r, Offset(x + lookX, y + lookY)) }
        }
        fun smile(width: Float = 12f, depth: Float = 7f, y: Float = 38f) =
            curve(32 - width / 2, y, 32f, y + depth, 32 + width / 2, y)
        fun frown() = curve(26f, 42f, 32f, 35f, 38f, 42f)
        fun heart(x: Float, y: Float) {
            val p =
                Path().apply {
                    moveTo(x, y + 4)
                    cubicTo(x - 9, y - 1, x - 4, y - 7, x, y - 3)
                    cubicTo(x + 4, y - 7, x + 9, y - 1, x, y + 4)
                }
            drawPath(p, ink)
        }
        fun drop(x: Float, y: Float, color: Color) {
            val p =
                Path().apply {
                    moveTo(x, y - 3)
                    cubicTo(x - 5, y + 2, x - 3, y + 5, x, y + 5)
                    cubicTo(x + 3, y + 5, x + 5, y + 2, x, y - 3)
                }
            drawPath(p, color)
        }
        when (mood.id) {
            "frustrated" -> {
                dots(29f)
                line(20f, 22f, 28f, 25f)
                line(36f, 25f, 44f, 22f)
                val p =
                    Path().apply {
                        moveTo(25f, 41f)
                        lineTo(29f, 39.5f)
                        lineTo(32f, 41f)
                        lineTo(35f, 39.5f)
                        lineTo(39f, 41f)
                    }
                drawPath(p, ink, style = pen)
            }
            "anxious" -> {
                dots(29f, r = 1.25f)
                curve(20f, 25f, 24f, 20f, 29f, 23f)
                curve(35f, 23f, 40f, 20f, 44f, 25f)
                curve(27f, 41f, 30f, 37.5f, 32f, 40f)
                curve(32f, 40f, 35f, 43f, 38f, 39f)
                drop(49f, 25f, Color(0xFF8DAFC7).copy(.8f))
            }
            "surprised" -> {
                dots(27f, r = 1.8f)
                curve(21f, 21f, 25f, 18f, 29f, 21f)
                curve(35f, 21f, 39f, 18f, 43f, 21f)
                drawOval(ink, Offset(28f, 35f), Size(8f, 11f), style = pen)
            }
            "excited" -> {
                listOf(25f, 39f).forEach { x ->
                    line(x, 23f, x, 31f)
                    line(x - 4, 27f, x + 4, 27f)
                    line(x - 2.5f, 24.5f, x + 2.5f, 29.5f)
                    line(x + 2.5f, 24.5f, x - 2.5f, 29.5f)
                }
                val p =
                    Path().apply {
                        moveTo(24f, 36f)
                        lineTo(40f, 36f)
                        cubicTo(39f, 50f, 25f, 50f, 24f, 36f)
                    }
                drawPath(p, ink)
                drawOval(Color(0xFFF4B5A6), Offset(28f, 42f), Size(8f, 4f))
            }
            "tense" -> {
                line(21f, 24f, 29f, 25f)
                line(35f, 25f, 43f, 24f)
                dots(29f, r = 1.1f)
                drawRoundRect(
                    Color.White.copy(.8f),
                    Offset(24f, 37f),
                    Size(16f, 7f),
                    CornerRadius(2f),
                )
                drawRoundRect(ink, Offset(24f, 37f), Size(16f, 7f), CornerRadius(2f), style = pen)
                line(25f, 40.5f, 39f, 40.5f)
                line(29f, 38f, 29f, 43f)
                line(35f, 38f, 35f, 43f)
            }
            "restless" -> {
                drawCircle(ink, 1.5f, Offset(23.5f, 29f))
                drawCircle(ink, 1.5f, Offset(40.5f, 27f))
                curve(20f, 24f, 24f, 21f, 28f, 23f)
                line(36f, 23f, 43f, 21.5f)
                curve(28f, 41f, 34f, 37f, 39f, 39f)
                curve(47f, 28f, 51f, 30f, 48f, 32f)
                curve(49f, 33f, 53f, 35f, 50f, 37f)
            }
            "hopeful" -> {
                dots(28f, lookY = -1f, r = 1.8f)
                listOf(25f, 39f).forEach { x ->
                    drawCircle(Color.White, .6f, Offset(x - .4f, 26.2f))
                }
                curve(20f, 22f, 24f, 19f, 29f, 21f)
                curve(35f, 21f, 40f, 19f, 44f, 22f)
                smile(10f, 4f, 38.5f)
                line(49f, 19f, 49f, 24f)
                line(46.5f, 21.5f, 51.5f, 21.5f)
            }
            "happy" -> {
                curve(21f, 29f, 25f, 23f, 29f, 29f)
                curve(35f, 29f, 39f, 23f, 43f, 29f)
                smile(16f, 11f, 36.5f)
            }
            "sad" -> {
                curve(20f, 24f, 25f, 25f, 29f, 21f)
                curve(35f, 21f, 39f, 25f, 44f, 24f)
                dots(30f, r = 1.3f)
                frown()
                drop(44f, 33f, Color(0xFF86B6D7).copy(.85f))
            }
            "low" -> {
                dots(30f, r = 1.1f)
                line(21f, 28f, 29f, 29f)
                line(35f, 29f, 43f, 28f)
                line(28f, 41f, 36f, 41f)
            }
            "calm" -> {
                curve(21f, 28f, 25f, 30f, 29f, 28f)
                curve(35f, 28f, 39f, 30f, 43f, 28f)
                smile(8f, 3f, 39f)
            }
            "loved" -> {
                heart(25f, 27f)
                heart(39f, 27f)
                smile(11f, 6f, 38f)
                drawCircle(Color(0xFFE6ACAA).copy(.55f), 3.1f, Offset(19f, 36f))
                drawCircle(Color(0xFFE6ACAA).copy(.55f), 3.1f, Offset(45f, 36f))
            }
            "drained" -> {
                line(21f, 29f, 29f, 30f)
                line(35f, 30f, 43f, 29f)
                curve(22f, 33f, 25f, 35f, 28f, 33f, ink.copy(.35f))
                curve(36f, 33f, 39f, 35f, 42f, 33f, ink.copy(.35f))
                curve(28f, 42f, 32f, 40f, 36f, 42f)
                line(36f, 42f, 39f, 41f)
            }
            "tired" -> {
                curve(21f, 28f, 25f, 31f, 29f, 29f)
                curve(35f, 29f, 39f, 31f, 43f, 28f)
                drawOval(ink, Offset(29f, 37f), Size(6f, 9f), style = pen)
                line(46f, 19f, 51f, 19f)
                line(51f, 19f, 46f, 24f)
                line(46f, 24f, 51f, 24f)
            }
            "safe" -> {
                dots(28f, r = 1.4f)
                smile(13f, 6f, 38f)
                val p =
                    Path().apply {
                        moveTo(47f, 19f)
                        lineTo(52f, 21f)
                        lineTo(51.5f, 26f)
                        quadraticTo(49f, 30f, 46.5f, 26f)
                        lineTo(46f, 21f)
                        close()
                    }
                drawPath(p, ink.copy(.6f), style = Stroke(1f))
                line(47.5f, 24f, 49f, 25.5f)
                line(49f, 25.5f, 51f, 22.5f)
            }
            "relieved" -> {
                curve(21f, 27f, 25f, 32f, 29f, 28f)
                curve(35f, 28f, 39f, 32f, 43f, 27f)
                drawOval(ink, Offset(29f, 38f), Size(6f, 3.8f), style = pen)
                curve(38f, 40f, 44f, 36f, 47f, 39f, ink.copy(.55f))
                curve(40f, 43f, 46f, 40f, 49f, 43f, ink.copy(.4f))
            }
            else -> {
                dots()
                line(28f, 40f, 36f, 40f)
            }
        }
    }
}
