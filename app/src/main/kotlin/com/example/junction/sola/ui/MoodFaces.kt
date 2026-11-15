package com.example.junction.sola.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import com.example.junction.sola.data.SolaMood

@Composable
fun MoodFace(mood: SolaMood, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawMoodFace(mood) }
}

/** Small pearly characters, each with its own expression, even without colour. */
internal fun DrawScope.drawMoodFace(mood: SolaMood) {
    val tint = Color(mood.color)
    val radius = size.minDimension / 2
    drawCircle(
        Brush.radialGradient(
            listOf(lerp(tint, Color.White, .76f), lerp(tint, Color.White, .12f), tint),
            center - Offset(radius * .34f, radius * .42f),
            radius * 1.85f,
        ),
        radius,
    )
    drawCircle(Color.White.copy(.72f), radius - .7f, style = Stroke(1.1f))
    scale(size.width / 64f, size.height / 64f, Offset.Zero) {
        val ink = Color(0xFF6B584F)
        val blush = Color(0xFFE9A39F)
        val rose = Color(0xFFDFA29B)

        fun curve(
            x: Float,
            y: Float,
            cx: Float,
            cy: Float,
            x2: Float,
            y2: Float,
            color: Color = ink,
            width: Float = 1.65f,
        ) {
            drawPath(
                Path().apply {
                    moveTo(x, y)
                    quadraticTo(cx, cy, x2, y2)
                },
                color,
                style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        fun eye(
            x: Float,
            y: Float = 31.5f,
            w: Float = 4.6f,
            h: Float = 5.4f,
            shine: Boolean = true,
        ) {
            drawOval(ink, Offset(x - w / 2, y - h / 2), Size(w, h))
            if (shine) drawCircle(Color.White.copy(.92f), .72f, Offset(x - w * .16f, y - h * .21f))
        }
        fun eyes(y: Float = 31.5f, w: Float = 4.6f, h: Float = 5.4f) {
            eye(24.5f, y, w, h)
            eye(39.5f, y, w, h)
        }
        fun cheek(x: Float, strength: Float = 1f) {
            drawOval(
                Brush.radialGradient(
                    listOf(blush.copy(.44f * strength), blush.copy(0f)),
                    Offset(x, 38f),
                    4.8f,
                ),
                Offset(x - 5f, 34.8f),
                Size(10f, 6.4f),
            )
        }
        fun smile(width: Float = 7f, depth: Float = 4f, y: Float = 40f) =
            curve(32f - width / 2, y, 32f, y + depth, 32f + width / 2, y)
        fun tinyW() {
            curve(27.5f, 40f, 29.5f, 44.2f, 32f, 40.8f)
            curve(32f, 40.8f, 34.5f, 44.2f, 36.5f, 40f)
        }
        fun drop(x: Float, y: Float, small: Boolean = false) {
            val width = if (small) 2.1f else 2.7f
            val p =
                Path().apply {
                    moveTo(x, y - 3.2f)
                    cubicTo(x - width * 1.7f, y + 1.8f, x - width, y + 4f, x, y + 4f)
                    cubicTo(x + width, y + 4f, x + width * 1.7f, y + 1.8f, x, y - 3.2f)
                }
            drawPath(p, Color(0xFF96BDD4).copy(.8f))
            curve(
                x - .8f,
                y + .3f,
                x - 1.4f,
                y + 1.4f,
                x - .7f,
                y + 2.2f,
                Color.White.copy(.7f),
                .85f,
            )
        }
        fun openSmile() {
            val p =
                Path().apply {
                    moveTo(26.5f, 38.8f)
                    quadraticTo(32f, 40.3f, 37.5f, 38.8f)
                    cubicTo(36.8f, 49f, 27.2f, 49f, 26.5f, 38.8f)
                    close()
                }
            drawPath(p, ink)
            drawOval(rose, Offset(29.1f, 43f), Size(5.8f, 2.6f))
        }
        fun heart(x: Float) {
            val p =
                Path().apply {
                    moveTo(x, 35f)
                    cubicTo(x - 8f, 30f, x - 4.5f, 25.3f, x, 29f)
                    cubicTo(x + 4.5f, 25.3f, x + 8f, 30f, x, 35f)
                    close()
                }
            drawPath(p, Color(0xFFB97878))
            drawOval(Color.White.copy(.55f), Offset(x - 3f, 28.8f), Size(1.7f, 1.1f))
        }
        fun sparkle(x: Float, y: Float) {
            val p =
                Path().apply {
                    moveTo(x, y - 2.7f)
                    quadraticTo(x + .35f, y - .35f, x + 2.3f, y)
                    quadraticTo(x + .35f, y + .35f, x, y + 2.7f)
                    quadraticTo(x - .35f, y + .35f, x - 2.3f, y)
                    quadraticTo(x - .35f, y - .35f, x, y - 2.7f)
                    close()
                }
            drawPath(p, ink.copy(.52f))
        }

        // Soft cheeks and warm ink hold the set together. Expressions carry the meaning.
        val cheekStrength = if (mood.id in listOf("drained", "low")) .6f else 1f
        cheek(17.5f, cheekStrength)
        cheek(46.5f, cheekStrength)
        when (mood.id) {
            "frustrated" -> {
                eyes(32f, 4.2f, 4.6f)
                curve(21f, 26f, 24.5f, 26.5f, 27.5f, 28f, width = 1.4f)
                curve(36.5f, 28f, 39.5f, 26.5f, 43f, 26f, width = 1.4f)
                curve(29f, 42f, 32f, 38.7f, 35f, 42f)
                curve(30.5f, 43.4f, 32f, 44f, 33.5f, 43.4f, rose.copy(.7f), 1f)
            }
            "anxious" -> {
                eyes(32f, 4.1f, 5.4f)
                curve(21f, 26f, 24f, 23.4f, 27f, 24.5f, width = 1.15f)
                curve(37f, 24.5f, 40f, 23.4f, 43f, 26f, width = 1.15f)
                curve(28.5f, 41.5f, 30.5f, 39f, 32f, 41.2f)
                curve(32f, 41.2f, 33.5f, 43.3f, 35.5f, 40.8f)
                drop(47f, 28f, small = true)
            }
            "surprised" -> {
                eyes(30.8f, 5.2f, 6.2f)
                curve(21.5f, 24.5f, 24.5f, 22f, 27.5f, 24.5f, width = 1.1f)
                curve(36.5f, 24.5f, 39.5f, 22f, 42.5f, 24.5f, width = 1.1f)
                drawOval(ink, Offset(29.7f, 38.6f), Size(4.6f, 6f))
                drawOval(rose.copy(.85f), Offset(30.6f, 42.4f), Size(2.8f, 1.3f))
            }
            "excited" -> {
                curve(20.7f, 32f, 24.5f, 24f, 28.3f, 32f)
                curve(35.7f, 32f, 39.5f, 24f, 43.3f, 32f)
                openSmile()
                sparkle(47f, 23f)
            }
            "tense" -> {
                eyes(32f, 4.8f, 3.8f)
                curve(21.5f, 26.7f, 24.5f, 26f, 27.5f, 27.2f, width = 1.3f)
                curve(36.5f, 27.2f, 39.5f, 26f, 42.5f, 26.7f, width = 1.3f)
                val mouth =
                    Path().apply {
                        moveTo(28.5f, 39.8f)
                        cubicTo(30.5f, 38.5f, 33.5f, 38.5f, 35.5f, 39.8f)
                        cubicTo(37f, 43.5f, 27f, 43.5f, 28.5f, 39.8f)
                        close()
                    }
                drawPath(mouth, Color.White.copy(.9f))
                drawPath(mouth, ink, style = Stroke(1.3f, cap = StrokeCap.Round))
            }
            "restless" -> {
                eye(25.3f, 32.1f, 4.5f, 5.1f)
                eye(40.3f, 30.8f, 4.5f, 5.1f)
                curve(21f, 26.7f, 24.5f, 23.4f, 28f, 25.3f, width = 1.15f)
                curve(37f, 25.3f, 40f, 25.3f, 43f, 24.8f, width = 1.15f)
                curve(29f, 41.8f, 32f, 39.3f, 35f, 40.5f)
                curve(48f, 30f, 50f, 31.5f, 48.5f, 33f, ink.copy(.45f), 1.1f)
            }
            "hopeful" -> {
                eyes(30.2f, 5.4f, 6.3f)
                curve(21f, 24f, 24.5f, 21.7f, 28f, 24f, width = 1.1f)
                curve(36f, 24f, 39.5f, 21.7f, 43f, 24f, width = 1.1f)
                smile(6f, 4.4f)
                drawCircle(Color.White.copy(.7f), .45f, Offset(25.3f, 31.4f))
                drawCircle(Color.White.copy(.7f), .45f, Offset(40.3f, 31.4f))
            }
            "happy" -> {
                curve(20.8f, 32f, 24.5f, 25.5f, 28.2f, 32f)
                curve(35.8f, 32f, 39.5f, 25.5f, 43.2f, 32f)
                tinyW()
            }
            "sad" -> {
                eyes(32f, 4.6f, 5.8f)
                curve(21f, 26.2f, 24.5f, 26.2f, 27.5f, 23.6f, width = 1.15f)
                curve(36.5f, 23.6f, 39.5f, 26.2f, 43f, 26.2f, width = 1.15f)
                curve(29f, 42.3f, 32f, 38.5f, 35f, 42.3f)
                drop(42f, 38f, small = true)
            }
            "low" -> {
                eyes(33f, 4.5f, 2.8f)
                curve(21.5f, 27.5f, 24.5f, 27.5f, 27.5f, 26f, ink.copy(.7f), 1.05f)
                curve(36.5f, 26f, 39.5f, 27.5f, 42.5f, 27.5f, ink.copy(.7f), 1.05f)
                curve(29.5f, 41.5f, 32f, 41f, 34.5f, 41.5f)
            }
            "calm" -> {
                curve(21f, 31f, 24.5f, 34.5f, 28f, 31f)
                curve(36f, 31f, 39.5f, 34.5f, 43f, 31f)
                smile(6f, 2.5f)
            }
            "loved" -> {
                heart(24.5f)
                heart(39.5f)
                smile(7f, 5f, 40.5f)
                cheek(17.5f, .25f)
                cheek(46.5f, .25f)
            }
            "drained" -> {
                drawOval(ink.copy(.07f), Offset(21f, 33.5f), Size(7f, 2.4f))
                drawOval(ink.copy(.07f), Offset(36f, 33.5f), Size(7f, 2.4f))
                curve(21f, 31.8f, 24.5f, 32.8f, 28f, 32.3f)
                curve(36f, 32.3f, 39.5f, 32.8f, 43f, 31.8f)
                curve(29f, 42f, 31f, 40.7f, 32.5f, 42f)
                curve(32.5f, 42f, 34f, 43f, 35.5f, 41.8f)
            }
            "tired" -> {
                curve(21f, 31f, 24.5f, 35f, 28f, 32f)
                curve(36f, 32f, 39.5f, 35f, 43f, 31f)
                curve(21.5f, 26.3f, 24.5f, 25.2f, 27.5f, 26.3f, ink.copy(.45f), 1f)
                drawOval(ink, Offset(29.7f, 38.8f), Size(4.6f, 6.6f))
                drawOval(rose, Offset(30.7f, 43.1f), Size(2.6f, 1.4f))
            }
            "safe" -> {
                eyes(31.5f, 4.1f, 4.4f)
                smile(8f, 5f)
                curve(21.5f, 25.7f, 24.5f, 24.5f, 27.5f, 25.7f, ink.copy(.5f), 1f)
                curve(36.5f, 25.7f, 39.5f, 24.5f, 42.5f, 25.7f, ink.copy(.5f), 1f)
            }
            "relieved" -> {
                curve(21f, 30.5f, 24.5f, 35.8f, 28f, 31f)
                curve(36f, 31f, 39.5f, 35.8f, 43f, 30.5f)
                drawOval(ink, Offset(30f, 39.8f), Size(4f, 2.8f))
                curve(38.5f, 40.6f, 42f, 38.5f, 44f, 40.3f, ink.copy(.38f), 1f)
            }
            else -> {
                eyes()
                smile()
            }
        }
    }
}
