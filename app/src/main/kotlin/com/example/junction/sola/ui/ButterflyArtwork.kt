package com.example.junction.sola.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.core.graphics.PathParser
import kotlin.math.PI
import kotlin.math.sin

private val upperLeft =
    PathParser.createPathFromPathData(
            "M 17.5 16 C 12 8, 4 9, 5.5 16.5 C 6.5 21, 13 20.5, 17.5 18 Z"
        )
        .asComposePath()
private val lowerLeft =
    PathParser.createPathFromPathData(
            "M 17.5 18.5 C 12 20, 7 25, 10 28 C 13 30.5, 16.5 25, 17.5 20.5 Z"
        )
        .asComposePath()
private val upperRight =
    PathParser.createPathFromPathData(
            "M 18.5 16 C 24 8, 32 9, 30.5 16.5 C 29.5 21, 23 20.5, 18.5 18 Z"
        )
        .asComposePath()
private val lowerRight =
    PathParser.createPathFromPathData(
            "M 18.5 18.5 C 24 20, 29 25, 26 28 C 23 30.5, 19.5 25, 18.5 20.5 Z"
        )
        .asComposePath()
private val antennae =
    PathParser.createPathFromPathData("M 17.5 13 Q 15 9.5 13.5 9.5 M 18.5 13 Q 21 9.5 22.5 9.5")
        .asComposePath()

@Composable
internal fun ButterflyArtwork(
    modifier: Modifier,
    tint: Color = Lavender,
    floating: Boolean = false,
    phase: Float = 0f,
) {
    val clock = sceneClock()
    val motion = LocalSceneMotion.current
    Canvas(modifier) {
        drawButterfly(if (motion) clock.value + phase else 0f, tint, floating && motion)
    }
}

/** Original frontend's 36-unit butterfly paths and 0.65-second wing beat. */
internal fun DrawScope.drawButterfly(
    seconds: Float,
    tint: Color = Lavender,
    floating: Boolean = false,
) {
    scale(size.width / 36f, size.height / 36f, Offset.Zero) {
        val drift = if (floating) sin(seconds / 6.5f * 2 * PI).toFloat() else 0f
        translate(drift * 4f, if (floating) -SolaMotion.cycle(seconds, 6.5f) * 7f else 0f) {
            rotate(drift * 4f, Offset(18f, 18f)) {
                scale(SolaMotion.wing(seconds), 1f, Offset(18f, 18f)) {
                    drawPath(upperLeft, tint.copy(.9f))
                    drawPath(upperRight, tint.copy(.9f))
                    drawPath(lowerLeft, lerp(tint, Color.White, .35f))
                    drawPath(lowerRight, lerp(tint, Color.White, .35f))
                    listOf(upperLeft, upperRight, lowerLeft, lowerRight).forEach {
                        drawPath(it, tint.copy(.75f), style = Stroke(.65f))
                    }
                    drawCircle(Color.White.copy(.75f), 1.4f, Offset(10.5f, 14.5f))
                    drawCircle(Color.White.copy(.75f), 1.4f, Offset(25.5f, 14.5f))
                }
                val body = Color(0xFF5A524A)
                drawLine(body, Offset(18f, 12.5f), Offset(18f, 23.5f), 1.5f, StrokeCap.Round)
                drawPath(antennae, body, style = Stroke(.8f, cap = StrokeCap.Round))
            }
        }
    }
}
