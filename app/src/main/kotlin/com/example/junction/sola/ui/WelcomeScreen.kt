package com.example.junction.sola.ui

import android.content.Context
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.Xml
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.junction.sola.data.SolaContent
import com.example.junction.sola.data.SolaScreen
import com.example.junction.sola.data.SolaStore
import kotlinx.coroutines.isActive
import org.xmlpull.v1.XmlPullParser

/** Timings and easing from the supplied welcome SVG/CSS, independent of Android animator scale. */
internal object WelcomeTiming {
    const val DURATION_MS = 2200L
    private val inkEase = CubicBezierEasing(.22f, 1f, .36f, 1f)
    private val fadeEase = CubicBezierEasing(.16f, 1f, .3f, 1f)

    fun ink(elapsedMs: Long) =
        inkEase.transform((elapsedMs.toFloat() / DURATION_MS).coerceIn(0f, 1f))

    fun subtitle(elapsedMs: Long) =
        fadeEase.transform(((elapsedMs - 1300L) / 900f).coerceIn(0f, 1f))
}

@Composable
internal fun welcomeElapsed(replay: Int): Long {
    val motion = LocalSceneMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var elapsed by
        remember(replay, motion) {
            mutableLongStateOf(if (motion) 0L else WelcomeTiming.DURATION_MS)
        }
    LaunchedEffect(replay, motion, lifecycle) {
        if (!motion) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val resumedAt = elapsed
            var firstFrame = 0L
            while (isActive && elapsed < WelcomeTiming.DURATION_MS) withFrameNanos { frame ->
                if (firstFrame == 0L) firstFrame = frame
                elapsed =
                    (resumedAt + (frame - firstFrame) / 1_000_000L).coerceAtMost(
                        WelcomeTiming.DURATION_MS
                    )
            }
        }
    }
    return elapsed
}

/** A real pen stroke, rather than revealing or clipping a pre-rendered word. */
internal class HelloLettering(context: Context) {
    private val path: Path
    private val measure: PathMeasure

    init {
        val parser = Xml.newPullParser()
        var data: String? = null
        context.assets.open("sola/welcome-hello.svg").use { input ->
            parser.setInput(input, "UTF-8")
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "path") {
                    data = parser.getAttributeValue(null, "d")
                    break
                }
                parser.next()
            }
        }
        path =
            PathParser.createPathFromPathData(requireNotNull(data) { "Missing welcome lettering" })
        measure = PathMeasure(path, false)
    }

    fun draw(scope: DrawScope, progress: Float) =
        with(scope) {
            val amount = progress.coerceIn(0f, 1f)
            if (amount == 0f) return@with
            val visible =
                if (amount == 1f) path
                else Path().apply { measure.getSegment(0f, measure.length * amount, this, true) }
            val alpha = .45f + .55f * amount
            withTransform({
                scale(size.width / 346f, size.height / 88f, Offset.Zero)
                translate(-22f, -40f)
            }) {
                translate(1.4f, 2f) {
                    drawPath(
                        visible.asComposePath(),
                        Color(0xFF4A3A30).copy(alpha = .17f * alpha),
                        style = Stroke(4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
                drawPath(
                    visible.asComposePath(),
                    Color(0xFF4D3834).copy(alpha = alpha),
                    style = Stroke(4.1f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
}

@Composable
internal fun HandwrittenHello(progress: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lettering = remember(context) { HelloLettering(context) }
    Canvas(
        modifier.aspectRatio(346f / 88f).semantics {
            text = AnnotatedString("hello")
            heading()
        }
    ) {
        lettering.draw(this, progress)
    }
}

@Composable
internal fun WelcomeScreen(store: SolaStore, navigate: (SolaScreen) -> Unit) {
    var replay by remember { mutableIntStateOf(0) }
    val elapsed = welcomeElapsed(replay)
    val fade = WelcomeTiming.subtitle(elapsed)
    val garden = remember { SolaContent.demo() }
    val breath = sceneTime()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val compact = maxHeight < 700.dp
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Eyebrow("Sola")
                TextButton({
                    store.update { it.copy(onboardingComplete = true) }
                    navigate(SolaScreen.TODAY)
                }) {
                    Text("Skip to home", color = Muted, fontSize = 12.sp)
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.semantics { contentDescription = "Replay welcome animation" }
                        .clickable(role = Role.Button, onClickLabel = "Replay welcome animation") {
                            replay++
                        }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.width(if (compact) 244.dp else 280.dp)
                            .height(if (compact) 200.dp else 244.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        GlassOrb(
                            Modifier.size(if (compact) 120.dp else 144.dp),
                            Color(0xFFE9C5B1),
                            breath,
                        )
                        key(replay) {
                            GardenArtwork(
                                garden,
                                Modifier.width(if (compact) 224.dp else 252.dp)
                                    .align(Alignment.Center)
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            Brush.horizontalGradient(
                                                0f to Color.Transparent,
                                                .14f to Color.White,
                                                .86f to Color.White,
                                                1f to Color.Transparent,
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    }
                                    .clearAndSetSemantics {},
                                onDay = { replay++ },
                            )
                        }
                    }
                    Spacer(Modifier.height(if (compact) 18.dp else 24.dp))
                    HandwrittenHello(
                        WelcomeTiming.ink(elapsed),
                        Modifier.width(if (compact) 244.dp else 264.dp),
                    )
                    Text(
                        "thanks for dropping by",
                        Modifier.padding(top = 12.dp).offset(y = (8f * (1f - fade)).dp).alpha(fade),
                        color = Color(0xFF5B5046),
                        fontSize = if (compact) 20.sp else 22.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PrimaryButton("Begin", { navigate(SolaScreen.ACCOUNT) }, Modifier.fillMaxWidth())
                TextButton(
                    {
                        store.useSample(true)
                        navigate(SolaScreen.TODAY)
                    },
                    Modifier.padding(top = 8.dp, bottom = 16.dp),
                ) {
                    Text("Explore the sample garden", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}
