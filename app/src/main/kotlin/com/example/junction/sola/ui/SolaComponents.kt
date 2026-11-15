package com.example.junction.sola.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Ink = Color(0xFF28322B)
val Muted = Color(0xFF82786F)
val Sage = Color(0xFFA8C2AD)
val Lavender = Color(0xFFC7BBDB)
val LocalSceneMotion = staticCompositionLocalOf { true }

@Composable
fun SolaTheme(motion: Boolean = true, content: @Composable () -> Unit) {
    val gardenGrowth = remember { mutableMapOf<String, GardenGrowthMemory>() }
    CompositionLocalProvider(
        LocalSceneMotion provides motion,
        LocalGardenGrowth provides gardenGrowth,
    ) {
        MaterialTheme(
            colorScheme =
                lightColorScheme(
                    primary = Ink,
                    onPrimary = Color.White,
                    background = Color(0xFFFAF6EF),
                    surface = Color(0xFFFAF6EF),
                    onSurface = Ink,
                    secondary = Sage,
                ),
            typography =
                Typography(
                    bodyLarge =
                        TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, color = Ink),
                    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, color = Ink),
                    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                ),
            content = content,
        )
    }
}

/**
 * Frame time deliberately ignores the global animator scale, as does the original ambient artwork.
 */
@Composable
fun sceneTime(): Float {
    return sceneClock().value
}

@Composable
fun AmbientSurface(tone: String = "sage", content: @Composable BoxScope.() -> Unit) {
    val clock = sceneClock()
    val motion = LocalSceneMotion.current
    val palette =
        when (tone) {
            "dawn" -> listOf(Color(0xFFFDF9F4), Color(0xFFF7EFE9), Color(0xFFEEEBF5))
            "rose" -> listOf(Color(0xFFFDF8F3), Color(0xFFF9ECE7), Color(0xFFF4E6E2))
            "lavender" -> listOf(Color(0xFFFBF8F4), Color(0xFFF3EFF6), Color(0xFFEBE9F4))
            "warm" -> listOf(Color(0xFFFCF8F2), Color(0xFFF8EFE3), Color(0xFFF5E8DC))
            else -> listOf(Color(0xFFFAF8F2), Color(0xFFF1F4EC), Color(0xFFEAF0EA))
        }
    val background = palette.map { motionColor(it, 700).value }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(background))) {
        Canvas(Modifier.matchParentSize()) {
            val time = if (motion) clock.value else 0f
            val drift =
                if (motion)
                    Offset(
                        sin(time * 2 * PI.toFloat() / 9) * 5.dp.toPx(),
                        -8.dp.toPx() * SolaMotion.cycle(time, 9f),
                    )
                else Offset.Zero
            val float = if (motion) -10.dp.toPx() * SolaMotion.cycle(time, 7f) else 0f
            val breath = if (motion) SolaMotion.cycle(time, 5.5f) else 0f
            val peachCenter = Offset(size.width * .13f, size.height * .3f + float)
            val lavenderCenter = Offset(size.width * .95f, size.height * .64f) + drift
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFF2D4BF).copy(.32f), Color.Transparent),
                    peachCenter,
                    size.width * .8f,
                ),
                size.width * .8f,
                peachCenter,
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Lavender.copy(.20f), Color.Transparent),
                    lavenderCenter,
                    size.width,
                ),
                size.width,
                lavenderCenter,
            )
            val auraCenter = Offset(size.width * .3f, size.height * .92f)
            val auraRadius = size.width * (.62f + breath * .05f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Sage.copy(.09f + breath * .035f), Color.Transparent),
                    auraCenter,
                    auraRadius,
                ),
                auraRadius,
                auraCenter,
            )
        }
        content()
    }
}

fun Modifier.glass(radius: Dp = 28.dp, selected: Boolean = false): Modifier =
    this.shadow(
            if (selected) 9.dp else 5.dp,
            RoundedCornerShape(radius),
            ambientColor = Color(0xFF92857A).copy(.10f),
            spotColor = Color(0xFF92857A).copy(.08f),
        )
        .clip(RoundedCornerShape(radius))
        .background(
            Brush.linearGradient(
                listOf(Color.White.copy(if (selected) .85f else .60f), Color.White.copy(.38f))
            )
        )
        .border(
            1.dp,
            if (selected) Sage.copy(.75f) else Color.White.copy(.85f),
            RoundedCornerShape(radius),
        )

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.glass().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun Title(text: String, subtitle: String? = null, centered: Boolean = true) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Light,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (subtitle != null)
            Text(
                subtitle,
                Modifier.padding(top = 9.dp),
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, fontSize = 10.sp, letterSpacing = 2.sp, color = Muted)

@Composable
fun Body(text: String, modifier: Modifier = Modifier, centered: Boolean = false) =
    Text(
        text,
        modifier,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = Muted,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
    )

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick,
        modifier.pressMotion(interaction).heightIn(min = 50.dp),
        interactionSource = interaction,
        enabled = enabled,
        shape = RoundedCornerShape(100),
        colors =
            ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color(0xFFFCFAF6)),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 14.dp),
    ) {
        Text(text, fontSize = 14.sp, color = if (enabled) Color(0xFFFCFAF6) else Muted)
    }
}

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressMotion(interaction)
            .heightIn(min = 48.dp)
            .glass(24.dp)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            SolaIcon(icon, Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 13.sp)
    }
}

@Composable
fun RoundButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    dark: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    IconButton(
        onClick,
        modifier
            .pressMotion(interaction)
            .size(48.dp)
            .then(if (dark) Modifier.clip(CircleShape).background(Ink) else Modifier.glass(24.dp))
            .semantics { contentDescription = label },
        interactionSource = interaction,
    ) {
        SolaIcon(icon, Modifier.size(21.dp), if (dark) Color.White else Ink)
    }
}

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val background by motionColor(if (selected) Ink else Color.White.copy(.55f))
    val foreground by motionColor(if (selected) Color.White else Muted)
    Surface(
        onClick,
        modifier.pressMotion(interaction).heightIn(min = 44.dp),
        interactionSource = interaction,
        shape = RoundedCornerShape(22.dp),
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(.75f)),
    ) {
        Box(
            Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = foreground,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SolaField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value,
        onValue,
        modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 13.sp) },
        shape = RoundedCornerShape(20.dp),
        singleLine = !multiline,
        minLines = if (multiline) 4 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors =
            OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White.copy(.45f),
                focusedContainerColor = Color.White.copy(.7f),
                unfocusedBorderColor = Color.White,
                focusedBorderColor = Sage,
            ),
        textStyle = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, color = Ink),
    )
}

@Composable
fun TopBar(back: (() -> Unit)? = null, settings: (() -> Unit)? = null, label: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null) RoundButton("back", "Back", back) else Spacer(Modifier.size(48.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (label != null) Eyebrow(label)
        }
        if (settings != null) RoundButton("settings", "Settings", settings)
        else Spacer(Modifier.size(48.dp))
    }
}

@Composable
fun SolaDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(20.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFFFAF6F0))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontSize = 23.sp, fontWeight = FontWeight.Light)
                IconButton(onDismiss) { SolaIcon("close") }
            }
            content()
        }
    }
}

@Composable
fun SolaPhoto(source: String?, modifier: Modifier = Modifier, description: String = "Diary photo") {
    val context = LocalContext.current
    val bitmap by
        produceState<ImageBitmap?>(null, source) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        if (source == null) return@runCatching null
                        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                        val decoded =
                            if (source.startsWith("asset:"))
                                context.assets.open("sola/" + source.removePrefix("asset:")).use {
                                    BitmapFactory.decodeStream(it, null, options)
                                }
                            else
                                BitmapFactory.decodeFile(
                                    File(context.filesDir, source).absolutePath,
                                    options,
                                )
                        decoded?.asImageBitmap()
                    }
                        .getOrNull()
                }
        }
    if (bitmap != null)
        Image(
            bitmap!!,
            description,
            modifier.clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop,
        )
    else
        Box(
            modifier.clip(RoundedCornerShape(24.dp)).background(Sage.copy(.20f)),
            contentAlignment = Alignment.Center,
        ) {
            SolaIcon("photo", Modifier.size(24.dp), Muted)
        }
}

@Composable
fun GlassOrb(
    modifier: Modifier = Modifier,
    tint: Color = Sage,
    time: Float = 0f,
    halo: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension * .44f * (1f + .014f * sin(time * .9f))
            val c = center + Offset(0f, sin(time * .7f) * 3f)
            if (halo) {
                val breath = SolaMotion.cycle(time, 5.5f)
                val haloRadius = r * (1.28f + .11f * breath)
                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFFF2E2B8).copy(.24f + .08f * breath),
                            tint.copy(.12f),
                            Color.Transparent,
                        ),
                        c,
                        haloRadius,
                    ),
                    haloRadius,
                    c,
                )
            }
            drawCircle(
                Brush.radialGradient(listOf(tint.copy(.20f), Color.Transparent), c, r * 1.30f),
                r * 1.30f,
                c,
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(.85f), tint.copy(.38f), Color.White.copy(.36f)),
                    c - Offset(r * .35f, r * .45f),
                    r * 1.8f,
                ),
                r,
                c,
            )
            drawCircle(
                Brush.linearGradient(
                    listOf(Color.White.copy(.95f), tint.copy(.2f), Color.White.copy(.7f))
                ),
                r,
                c,
                style = Stroke(1.5.dp.toPx()),
            )
            drawArc(
                Color.White.copy(.6f),
                208f,
                75f,
                false,
                c - Offset(r * .77f, r * .77f),
                Size(r * 1.54f, r * 1.54f),
                style = Stroke(1.dp.toPx(), cap = StrokeCap.Round),
            )
            drawOval(Color.White.copy(.16f), c - Offset(r * .7f, r * .8f), Size(r * 1.2f, r * .62f))
        }
        content()
    }
}

@Composable
fun SolaIcon(name: String, modifier: Modifier = Modifier.size(22.dp), tint: Color = Ink) {
    if (name == "butterfly") {
        ButterflyArtwork(modifier, tint)
        return
    }
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val stroke = Stroke(1.45f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(a: Float, b: Float, c: Float, d: Float) =
                drawLine(tint, Offset(a, b), Offset(c, d), 1.45f, StrokeCap.Round)
            fun path(vararg points: Float) {
                val p = Path()
                p.moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) p.lineTo(points[i], points[i + 1])
                drawPath(p, tint, style = stroke)
            }
            when (name) {
                "plus" -> {
                    line(12f, 5f, 12f, 19f)
                    line(5f, 12f, 19f, 12f)
                }
                "close" -> {
                    line(6f, 6f, 18f, 18f)
                    line(18f, 6f, 6f, 18f)
                }
                "back" -> {
                    path(14f, 5f, 7f, 12f, 14f, 19f)
                    line(7f, 12f, 20f, 12f)
                }
                "next" -> path(9f, 5f, 16f, 12f, 9f, 19f)
                "check" -> path(5f, 12f, 10f, 17f, 20f, 7f)
                "sun" -> {
                    drawCircle(tint, 4f, Offset(12f, 12f), style = stroke)
                    repeat(8) {
                        val a = it * PI / 4
                        line(
                            (12 + 7 * cos(a)).toFloat(),
                            (12 + 7 * sin(a)).toFloat(),
                            (12 + 9.3 * cos(a)).toFloat(),
                            (12 + 9.3 * sin(a)).toFloat(),
                        )
                    }
                }
                "flower" -> {
                    repeat(5) {
                        rotate(it * 72f, Offset(12f, 10f)) {
                            drawOval(tint, Offset(9f, 3f), Size(6f, 8f), style = stroke)
                        }
                    }
                    drawCircle(tint, 2f, Offset(12f, 10f))
                    path(12f, 14f, 12f, 22f)
                    path(12f, 19f, 7f, 17f)
                }
                "stats" -> {
                    path(4f, 4f, 4f, 20f, 21f, 20f)
                    path(7f, 15f, 11f, 10f, 15f, 13f, 20f, 6f)
                }
                "heart" -> {
                    val p = Path()
                    p.moveTo(12f, 20f)
                    p.cubicTo(-3f, 11f, 4f, -1f, 12f, 7f)
                    p.cubicTo(20f, -1f, 27f, 11f, 12f, 20f)
                    drawPath(p, tint, style = stroke)
                }
                "settings" -> {
                    drawCircle(tint, 3.3f, Offset(12f, 12f), style = stroke)
                    repeat(8) {
                        rotate(it * 45f, Offset(12f, 12f)) {
                            path(9f, 4f, 10f, 2.5f, 14f, 2.5f, 15f, 4f)
                        }
                    }
                    drawCircle(tint, 8f, Offset(12f, 12f), style = stroke)
                }
                "bulb" -> {
                    drawCircle(tint, 6f, Offset(12f, 9f), style = stroke)
                    drawRect(Color.Transparent, Offset(8f, 13f), Size(8f, 4f))
                    path(8f, 13f, 9f, 18f, 15f, 18f, 16f, 13f)
                    line(10f, 21f, 14f, 21f)
                }
                "photo" -> {
                    drawRoundRect(
                        tint,
                        Offset(3f, 5f),
                        Size(18f, 15f),
                        CornerRadius(3f),
                        style = stroke,
                    )
                    drawCircle(tint, 2f, Offset(9f, 10f), style = stroke)
                    path(4f, 18f, 9f, 13f, 13f, 17f, 16f, 12f, 21f, 17f)
                }
                "mic" -> {
                    drawRoundRect(
                        tint,
                        Offset(9f, 2f),
                        Size(6f, 13f),
                        CornerRadius(3f),
                        style = stroke,
                    )
                    drawArc(tint, 0f, 180f, false, Offset(6f, 7f), Size(12f, 12f), style = stroke)
                    line(12f, 19f, 12f, 23f)
                    line(8f, 23f, 16f, 23f)
                }
                "phone" -> {
                    drawRoundRect(
                        tint,
                        Offset(7f, 2f),
                        Size(10f, 20f),
                        CornerRadius(2f),
                        style = stroke,
                    )
                    line(10f, 19f, 14f, 19f)
                }
                "watch" -> {
                    drawRoundRect(
                        tint,
                        Offset(6f, 6f),
                        Size(12f, 12f),
                        CornerRadius(4f),
                        style = stroke,
                    )
                    path(8f, 6f, 9f, 1f, 15f, 1f, 16f, 6f)
                    path(8f, 18f, 9f, 23f, 15f, 23f, 16f, 18f)
                    path(12f, 9f, 12f, 12f, 15f, 13f)
                }
                "music" -> {
                    path(8f, 17f, 8f, 5f, 20f, 2f, 20f, 14f)
                    drawOval(tint, Offset(2f, 16f), Size(6f, 5f), style = stroke)
                    drawOval(tint, Offset(14f, 13f), Size(6f, 5f), style = stroke)
                }
                "call" -> {
                    val p = Path()
                    p.moveTo(5f, 2f)
                    p.lineTo(9f, 7f)
                    p.lineTo(6f, 10f)
                    p.quadraticTo(9f, 16f, 14f, 18f)
                    p.lineTo(17f, 15f)
                    p.lineTo(22f, 19f)
                    p.quadraticTo(18f, 26f, 9f, 18f)
                    p.quadraticTo(0f, 10f, 5f, 2f)
                    drawPath(p, tint, style = stroke)
                }
                "butterfly" -> {
                    repeat(2) {
                        val sign = if (it == 0) 1f else -1f
                        val p = Path()
                        p.moveTo(12f, 12f)
                        p.cubicTo(12f + sign * 13f, -5f, 12f + sign * 13f, 15f, 12f, 13f)
                        p.cubicTo(12f + sign * 10f, 11f, 12f + sign * 7f, 27f, 12f, 15f)
                        drawPath(p, tint.copy(.65f))
                    }
                    line(12f, 9f, 12f, 19f)
                }
                "send" -> {
                    path(2f, 10f, 22f, 2f, 15f, 22f, 11f, 13f, 2f, 10f)
                    line(11f, 13f, 22f, 2f)
                }
                "replay" -> {
                    drawArc(tint, -60f, 300f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke)
                    path(5f, 2f, 4f, 8f, 10f, 8f)
                }
                "trash" -> {
                    path(5f, 6f, 6f, 21f, 18f, 21f, 19f, 6f)
                    line(3f, 6f, 21f, 6f)
                    path(9f, 6f, 9f, 2f, 15f, 2f, 15f, 6f)
                    line(10f, 10f, 10f, 17f)
                    line(14f, 10f, 14f, 17f)
                }
                else -> {
                    drawCircle(tint, 8f, Offset(12f, 12f), style = stroke)
                    drawCircle(tint, 1f, Offset(12f, 8f))
                    line(12f, 12f, 12f, 17f)
                }
            }
        }
    }
}
