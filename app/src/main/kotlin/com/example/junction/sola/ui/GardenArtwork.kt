package com.example.junction.sola.ui

import android.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.example.junction.sola.data.*
import kotlin.math.*
import org.json.JSONArray

private data class Twig(
    val path: android.graphics.Path,
    val x: Float,
    val y: Float,
    val angle: Float,
    val color: Color,
)

private data class Branch(
    val path: android.graphics.Path,
    val width: Float,
    val x: Float,
    val y: Float,
    val radius: Float,
    val twigs: List<Twig>,
)

private fun nativePath(value: String) = PathParser.createPathFromPathData(value)

private fun DrawScope.growPath(
    path: android.graphics.Path,
    color: Color,
    width: Float,
    progress: Float = 1f,
) {
    if (progress <= 0f) return
    val grown =
        if (progress >= 1f) path
        else
            android.graphics.Path().apply {
                val m = PathMeasure(path, false)
                m.getSegment(0f, m.length * progress, this, true)
            }
    drawPath(
        grown.asComposePath(),
        color,
        style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * Original sixteen-stage SVG geometry, rendered natively; extra daily memories use the fine twig
 * tips.
 */
@Composable
fun GardenArtwork(
    state: SolaState,
    modifier: Modifier = Modifier,
    replay: Int = 0,
    onDay: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val branches = remember {
        val json =
            JSONArray(
                context.assets.open("sola/branches.json").bufferedReader().use { it.readText() }
            )
        (0 until json.length()).map { i ->
            val row = json.getJSONObject(i)
            val twigs = row.getJSONArray("twigs")
            Branch(
                nativePath(row.getString("path")),
                row.getDouble("strokeWidth").toFloat(),
                row.getDouble("tipX").toFloat(),
                row.getDouble("tipY").toFloat(),
                row.getDouble("size").toFloat(),
                (0 until twigs.length()).map { j ->
                    val t = twigs.getJSONObject(j)
                    Twig(
                        nativePath(t.getString("d")),
                        t.getDouble("tipX").toFloat(),
                        t.getDouble("tipY").toFloat(),
                        t.getDouble("angle").toFloat(),
                        Color(android.graphics.Color.parseColor(t.getString("petalColor"))),
                    )
                },
            )
        }
    }
    val month = SolaDates.key().take(7)
    val days = state.loggedDates.filter { it.startsWith(month) }.takeLast(31)
    val time = key(replay) { sceneTime() }
    val motion = LocalSceneMotion.current
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(390f / 340f)) {
        val unit = maxWidth / 390f
        Canvas(Modifier.fillMaxSize()) {
            scale(size.width / 390f, size.height / 340f, Offset.Zero) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFF4D6C2).copy(.45f), Color.Transparent),
                        Offset(195f, 146f),
                        168f,
                    ),
                    168f,
                    Offset(195f, 146f),
                )
                drawPath(
                    nativePath("M 0 276 Q 96 260 198 272 T 390 268 L 390 340 L 0 340 Z")
                        .asComposePath(),
                    Brush.verticalGradient(
                        listOf(Color(0xFFB7C9B5).copy(.30f), Color.Transparent),
                        270f,
                        340f,
                    ),
                )
                drawPath(
                    nativePath("M 0 290 Q 195 274 390 290 L 390 340 L 0 340 Z").asComposePath(),
                    Brush.verticalGradient(
                        listOf(Color(0xFFBFCDB6).copy(.18f), Color.Transparent),
                        280f,
                        340f,
                    ),
                )
                growPath(
                    nativePath("M 22 279 Q 88 276 146 278 Q 195 279 252 276 Q 312 275 368 278"),
                    Color(0xFF9CA88B).copy(.3f),
                    .7f,
                )
                val branchColor = Color(0xFF887260)
                growPath(
                    nativePath(
                        if (days.isEmpty()) "M 195 278 Q 194 268 195 258"
                        else "M 195 278 Q 193.5 244 195 214 Q 195.5 204 195 198"
                    ),
                    branchColor,
                    5f,
                )
                if (days.isEmpty()) {
                    rotate(-35f, Offset(195f, 260f)) {
                        drawOval(Color(0xFFB9CBB0), Offset(182f, 248f), Size(13f, 7f))
                    }
                    rotate(25f, Offset(195f, 262f)) {
                        drawOval(Color(0xFFC8D7BD), Offset(195f, 254f), Size(13f, 7f))
                    }
                }
                branches.take(days.size.coerceAtMost(16)).forEachIndexed { i, b ->
                    val p = if (motion) ((time - i * .12f) * 1.8f).coerceIn(0f, 1f) else 1f
                    growPath(b.path, branchColor.copy(.88f), b.width, p)
                    b.twigs.forEach { t ->
                        growPath(
                            t.path,
                            branchColor.copy(.65f),
                            .85f,
                            (p * 1.8f - .65f).coerceIn(0f, 1f),
                        )
                        if (p > .7f)
                            rotate(t.angle + sin(time * .6f + i) * 2f, Offset(t.x, t.y)) {
                                drawOval(t.color.copy(.8f), Offset(t.x - 3, t.y - 7), Size(6f, 11f))
                                drawOval(
                                    Color.White.copy(.35f),
                                    Offset(t.x - 2, t.y - 6),
                                    Size(2f, 7f),
                                )
                            }
                    }
                }
                days.forEachIndexed { i, day ->
                    val b = branches[i % 16]
                    val x = if (i < 16) b.x else b.twigs.first().x
                    val y = if (i < 16) b.y else b.twigs.first().y
                    val p =
                        if (motion) ((time - i.coerceAtMost(16) * .12f) * 2f - .5f).coerceIn(0f, 1f)
                        else 1f
                    val radius = (if (i < 16) b.radius else 13f) * p
                    if (radius > 0f) {
                        val mood = state.moodFor(day)
                        val color = Color(SolaContent.color(mood?.quadrant ?: "sage"))
                        val center = Offset(x, y)
                        drawCircle(
                            Brush.radialGradient(
                                listOf(color.copy(.4f), Color.Transparent),
                                center,
                                radius * 1.65f,
                            ),
                            radius * 1.65f,
                            center,
                        )
                        drawCircle(
                            Brush.radialGradient(
                                listOf(
                                    Color.White.copy(.95f),
                                    color.copy(.56f),
                                    Color.White.copy(.40f),
                                ),
                                center - Offset(radius * .3f, radius * .4f),
                                radius * 1.7f,
                            ),
                            radius,
                            center,
                        )
                        drawCircle(Color.White.copy(.9f), radius, center, style = Stroke(.9f))
                        drawArc(
                            Color.White.copy(.85f),
                            205f,
                            72f,
                            false,
                            center - Offset(radius * .75f, radius * .75f),
                            Size(radius * 1.5f, radius * 1.5f),
                            style = Stroke(.8f, cap = StrokeCap.Round),
                        )
                        drawCircle(color.copy(.6f), 2f, center + Offset(0f, radius * .35f))
                    }
                }
                repeat(state.flowerCount.coerceAtMost(14)) { i ->
                    val x =
                        listOf(
                            56f,
                            334f,
                            102f,
                            288f,
                            144f,
                            244f,
                            34f,
                            356f,
                            78f,
                            312f,
                            170f,
                            218f,
                            120f,
                            268f,
                        )[i]
                    val y = 290f + (i % 3) * 6
                    val sway = sin(time * .8f + i) * 1.5f
                    growPath(
                        nativePath("M $x ${y+8} Q ${x+sway} $y ${x+sway} ${y-15}"),
                        Color(0xFF91A77F),
                        .9f,
                    )
                    val petal =
                        listOf(Color(0xFFF2B8C6), Color(0xFFF6D39A), Lavender, Color(0xFFB9D6EB))[
                            i % 4]
                    repeat(5) { k ->
                        rotate(k * 72f, Offset(x + sway, y - 15)) {
                            drawOval(petal, Offset(x + sway - 2.5f, y - 22), Size(5f, 8f))
                        }
                    }
                    drawCircle(Color(0xFFFFF4D6), 2f, Offset(x + sway, y - 15))
                }
            }
        }
        days.forEachIndexed { i, day ->
            val b = branches[i % 16]
            val x = if (i < 16) b.x else b.twigs.first().x
            val y = if (i < 16) b.y else b.twigs.first().y
            Box(
                Modifier.offset(unit * x - 24.dp, unit * y - 24.dp)
                    .size(48.dp)
                    .semantics {
                        contentDescription =
                            "Memory for $day, ${state.moodFor(day)?.label ?: "diary entry"}"
                    }
                    .clickable { onDay(day) }
            )
        }
    }
}
