package com.example.junction.sola.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.isActive

/** Reference CSS timings, evaluated on frame time rather than Android's animator duration scale. */
internal object SolaMotion {
    private val softEase = CubicBezierEasing(.16f, 1f, .3f, 1f)
    private val growthEase = CubicBezierEasing(.22f, 1f, .36f, 1f)

    fun ease(progress: Float) = softEase.transform(progress.coerceIn(0f, 1f))

    fun growth(progress: Float) = growthEase.transform(progress.coerceIn(0f, 1f))

    fun cycle(seconds: Float, period: Float, phase: Float = 0f) =
        ((1 - cos((seconds / period + phase) * 2 * PI)) / 2).toFloat()

    fun bloom(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return if (p < .68f) 1.12f * growth(p / .68f) else 1.12f - .12f * ease((p - .68f) / .32f)
    }

    fun wing(seconds: Float) = 1f - .58f * cycle(seconds, .65f)
}

/** The clock pauses off-screen and resumes without fast-forwarding a growth/entrance animation. */
@Composable
internal fun sceneClock(stopAtSeconds: Float = Float.POSITIVE_INFINITY): State<Float> {
    val enabled = LocalSceneMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val seconds = remember(enabled) { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled, lifecycle) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val resumedAt = seconds.floatValue
            var first = 0L
            while (isActive && seconds.floatValue < stopAtSeconds) withFrameNanos { frame ->
                if (first == 0L) first = frame
                seconds.floatValue =
                    (resumedAt + (frame - first) / 1_000_000_000f).coerceAtMost(stopAtSeconds)
            }
        }
    }
    return seconds
}

@Composable
private fun <T> motionValue(target: T, durationMs: Int, interpolate: (T, T, Float) -> T): State<T> {
    val enabled = LocalSceneMotion.current
    val value = remember { mutableStateOf(target) }
    val interpolation by rememberUpdatedState(interpolate)
    LaunchedEffect(target, enabled) {
        if (!enabled || value.value == target) {
            value.value = target
            return@LaunchedEffect
        }
        val from = value.value
        var first = 0L
        var progress = 0f
        while (isActive && progress < 1f) withFrameNanos { frame ->
            if (first == 0L) first = frame
            progress = ((frame - first) / (durationMs * 1_000_000f)).coerceIn(0f, 1f)
            value.value = interpolation(from, target, SolaMotion.ease(progress))
        }
    }
    return value
}

@Composable
internal fun motionFloat(target: Float, durationMs: Int = 220): State<Float> =
    motionValue(target, durationMs) { from, to, p -> from + (to - from) * p }

@Composable
internal fun motionColor(target: Color, durationMs: Int = 220): State<Color> =
    motionValue(target, durationMs) { from, to, p -> lerp(from, to, p) }

@Composable
internal fun Modifier.pressMotion(interaction: InteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale = motionFloat(if (pressed) .97f else 1f, 150)
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
internal fun Modifier.motionClick(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return pressMotion(interaction)
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick,
        )
}

@Composable
internal fun Modifier.softEntrance(token: Any?, durationMs: Int = 320): Modifier {
    val enabled = LocalSceneMotion.current
    val clock = key(token) { sceneClock(durationMs / 1000f) }
    return graphicsLayer {
        val progress = if (enabled) SolaMotion.ease(clock.value * 1000 / durationMs) else 1f
        alpha = progress
        translationY = (1 - progress) * 8f * density
    }
}

@Composable
internal fun Modifier.softFloat(phase: Float = 0f): Modifier {
    val enabled = LocalSceneMotion.current
    val clock = sceneClock()
    return graphicsLayer {
        val t = clock.value
        translationY = if (enabled) -8f * density * SolaMotion.cycle(t, 9f, phase) else 0f
        translationX = if (enabled) sin((t / 9f + phase) * 2 * PI).toFloat() * 3f * density else 0f
    }
}

internal class GardenGrowthMemory(var days: Int = 0, var flowers: Int = 0, var replay: Int = 0)

internal val LocalGardenGrowth =
    staticCompositionLocalOf<MutableMap<String, GardenGrowthMemory>?> { null }

internal data class GardenGrowthPlan(
    val from: Int,
    val target: Int,
    val replaying: Boolean = false,
) {
    fun start(index: Int) =
        if (from == 0) .52f + index.coerceAtMost(16) * .26f
        else .12f + (index - from).coerceAtLeast(0) * .26f

    fun branch(index: Int, seconds: Float) =
        if (index < from) 1f else SolaMotion.growth((seconds - start(index)) / .78f)

    fun bud(index: Int, twig: Int, seconds: Float) =
        if (index < from) 1f
        else SolaMotion.bloom((seconds - start(index) - .34f - twig * .09f) / .5f)

    fun leaf(index: Int, seconds: Float) =
        if (index < from) 1f else SolaMotion.bloom((seconds - start(index)) / .62f)

    fun trunk(seconds: Float) = if (from > 0) 1f else SolaMotion.growth((seconds - .24f) / .65f)

    val duration: Float
        get() = if (from >= target) 0f else start(target - 1) + 1.3f
}
