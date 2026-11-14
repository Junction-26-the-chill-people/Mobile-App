package com.example.junction.sola.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.junction.sola.data.*
import kotlin.math.*

@Composable
fun MoodChart(
    logs: List<MoodLog>,
    range: Int,
    modifier: Modifier = Modifier,
    onDay: (String) -> Unit = {},
) {
    val dates =
        (range - 1 downTo 0).map {
            SolaDates.key(SolaDates.dayOffset(System.currentTimeMillis(), -it))
        }
    val values = dates.map { day ->
        logs
            .filter { SolaDates.key(it.timestamp) == day }
            .map { it.value }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()
    }
    val ink = Color(0xFF8C5966)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(180.dp).semantics {
            contentDescription = "Mood chart, $range days, ${values.count {it!=null}} logged days"
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val left = 14.dp.toPx()
            val top = 20.dp.toPx()
            val width = size.width - left * 2
            val height = size.height - top * 2
            fun point(i: Int, v: Float) =
                Offset(
                    left + width * i / (range - 1).coerceAtLeast(1),
                    top + height * (1 - v / 100f),
                )
            listOf(0f, 50f, 100f).forEach { v ->
                drawLine(Color.White.copy(.9f), point(0, v), point(range - 1, v), 1.dp.toPx())
            }
            val real = values.mapNotNull { it }
            if (real.isNotEmpty()) {
                val mean = real.average().toFloat()
                val bandTop = point(0, (mean + 14).coerceAtMost(100f)).y
                val bandBottom = point(0, (mean - 14).coerceAtLeast(0f)).y
                drawRoundRect(
                    Lavender.copy(.18f),
                    Offset(left, bandTop),
                    Size(width, bandBottom - bandTop),
                    androidx.compose.ui.geometry.CornerRadius(20f),
                )
                val points = values.mapIndexedNotNull { i, v -> v?.let { point(i, it) } }
                if (points.size > 1) {
                    val p = Path()
                    p.moveTo(points.first().x, points.first().y)
                    points.zipWithNext().forEach { (a, b) ->
                        val mid = (a.x + b.x) / 2
                        p.cubicTo(mid, a.y, mid, b.y, b.x, b.y)
                    }
                    val area =
                        Path().apply {
                            addPath(p)
                            lineTo(points.last().x, top + height)
                            lineTo(points.first().x, top + height)
                            close()
                        }
                    drawPath(
                        area,
                        Brush.verticalGradient(
                            listOf(ink.copy(.12f), Color.Transparent),
                            top,
                            top + height,
                        ),
                    )
                    drawPath(p, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
                values.forEachIndexed { i, v ->
                    if (v != null) {
                        val pos = point(i, v)
                        drawCircle(Color.White, 5.dp.toPx(), pos)
                        drawCircle(ink, 3.dp.toPx(), pos)
                    }
                }
            }
        }
        values.forEachIndexed { i, v ->
            if (v != null)
                Box(
                    Modifier.offset(
                            x = maxWidth * i / (range - 1).coerceAtLeast(1) - 18.dp,
                            y = (maxHeight - 40.dp) * (1 - v / 100f),
                        )
                        .size(40.dp)
                        .semantics { contentDescription = "Check-ins for ${dates[i]}" }
                        .clickable { onDay(dates[i]) }
                )
        }
    }
}

@Composable
fun StatisticsScreen(s: SolaState, navigate: (SolaScreen) -> Unit) {
    var range by rememberSaveable { mutableIntStateOf(7) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val earliest = SolaDates.key(SolaDates.dayOffset(System.currentTimeMillis(), 1 - range))
    val logs =
        s.moods.filter {
            SolaDates.key(it.timestamp) >= earliest &&
                SolaDates.key(it.timestamp) <= SolaDates.key()
        }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(
            settings = { navigate(SolaScreen.SETTINGS) },
            label = if (s.demo) "Sample data" else null,
        )
        Spacer(Modifier.height(16.dp))
        Title("Statistics", "Gentle patterns over time")
        Row(
            Modifier.padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(7, 14, 28).forEach { n ->
                ChoiceChip(
                    "${n}D",
                    n == range,
                    {
                        range = n
                        selected = null
                    },
                )
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("How things have moved", fontSize = 20.sp, fontWeight = FontWeight.Light)
            Body(
                if (logs.isEmpty()) "Your first check-in will start the story."
                else "Your own check-ins, a day at a time."
            )
            MoodChart(s.moods, range, onDay = { selected = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    SolaDates.format(
                        SolaDates.dayOffset(System.currentTimeMillis(), 1 - range),
                        "d MMM",
                    ),
                    color = Muted,
                    fontSize = 10.sp,
                )
                Text("Today", color = Muted, fontSize = 10.sp)
            }
            selected?.let { date ->
                HorizontalDivider(color = Color.White)
                Text(SolaDates.format(SolaDates.parseKey(date), "EEEE, d MMM"), fontSize = 13.sp)
                Body(
                    s.moods
                        .filter { SolaDates.key(it.timestamp) == date }
                        .joinToString(" · ") { it.label }
                )
            }
            if (logs.isNotEmpty())
                Text(
                    "${logs.size} check-ins · ${logs.map {SolaDates.key(it.timestamp)}.distinct().size} days",
                    fontSize = 11.sp,
                    color = Muted,
                )
        }
        GlassCard(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Eyebrow("A little reflection")
            Text("What might be behind this", fontSize = 20.sp, fontWeight = FontWeight.Light)
            Body(
                "Gemini reflections will appear here once the text service is connected. For now, you can explore your check-ins and diary in your garden."
            )
            Text("AI insights · coming later", fontSize = 11.sp, color = Muted)
        }
        GlassCard(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Text("The feelings you’ve noticed", fontSize = 18.sp, fontWeight = FontWeight.Light)
            val labels =
                logs.groupingBy { it.label }.eachCount().entries.sortedByDescending { it.value }
            if (labels.isEmpty()) Body("Every feeling has a place here.")
            labels.take(5).forEach { (label, count) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val quadrant = logs.first { it.label == label }.quadrant
                    Box(
                        Modifier.size(10.dp)
                            .background(Color(SolaContent.color(quadrant)), CircleShape)
                    )
                    Text(label, Modifier.weight(1f).padding(start = 10.dp), fontSize = 13.sp)
                    Text("$count", fontSize = 13.sp, color = Muted)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCard(Modifier.weight(1f)) {
                SolaIcon("watch", Modifier.size(22.dp), Muted)
                Text("Sleep", fontSize = 16.sp)
                Body("Not connected")
                Text("Watch integration later", fontSize = 10.sp, color = Muted)
            }
            GlassCard(Modifier.weight(1f)) {
                SolaIcon("sun", Modifier.size(22.dp), Muted)
                Text("Activity", fontSize = 16.sp)
                Body("Not connected")
                Text("Phone integration later", fontSize = 10.sp, color = Muted)
            }
        }
        Body(
            "A record of your feelings, not a clinical assessment.",
            Modifier.padding(vertical = 24.dp),
            true,
        )
    }
}

@Composable
fun SupportScreen(
    s: SolaState,
    navigate: (SolaScreen) -> Unit,
    send: (SolaFriend) -> Unit,
    notify: (String) -> Unit,
) {
    val context = LocalContext.current
    var callPicker by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val time = sceneTime()
    fun dial(phone: String) {
        runCatching {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
            }
            .onFailure { notify("No dialer is available on this device.") }
    }
    if (callPicker)
        SolaDialog("A familiar voice", { callPicker = false }) {
            val contacts = s.friends.filter { it.phone.isNotBlank() }
            contacts.forEach { f ->
                SoftButton(
                    "Call ${f.name}",
                    {
                        callPicker = false
                        dial(f.phone)
                    },
                    Modifier.fillMaxWidth(),
                    "call",
                )
            }
            if (s.profile.clinicPhone.isNotBlank())
                SoftButton(
                    "Call ${s.profile.clinic.ifBlank{"your clinic"}}",
                    {
                        callPicker = false
                        dial(s.profile.clinicPhone)
                    },
                    Modifier.fillMaxWidth(),
                    "call",
                )
            if (contacts.isEmpty() && s.profile.clinicPhone.isBlank())
                Body(
                    "Add a phone number for a friend or clinic in Settings to open your phone’s dialer here."
                )
            SoftButton(
                "Manage people",
                {
                    callPicker = false
                    navigate(SolaScreen.SETTINGS)
                },
                Modifier.fillMaxWidth(),
            )
        }
    if (sharing)
        SolaDialog("Your choice to share", { sharing = false }) {
            Body(
                "Your journal is stored on this phone. No clinician is receiving your entries yet. A secure clinic connection will be added later."
            )
            Body(
                if (s.profile.clinicianSharing) "Your preference to share has been saved."
                else "Sharing is currently switched off."
            )
            PrimaryButton("Understood", { sharing = false }, Modifier.fillMaxWidth())
        }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(settings = { navigate(SolaScreen.SETTINGS) })
        Spacer(Modifier.height(16.dp))
        Title("Always close.", "You don’t have to hold it all alone.")
        Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(272.dp)) {
                val r = size.minDimension / 2
                repeat(3) { i ->
                    drawCircle(
                        Color(0xFFCFB6AE).copy(.16f),
                        r * (.70f + i * .14f) + (sin(time * .8f) * 3),
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
            GlassOrb(
                Modifier.size(212.dp)
                    .clickable { callPicker = true }
                    .semantics { contentDescription = "One call away. Choose a contact." },
                Color(0xFFE3BFAE),
                time,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SolaIcon("call", Modifier.size(28.dp), Color(0xFF926F65))
                    Text(
                        "One call away",
                        Modifier.padding(top = 15.dp),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Eyebrow("Your people")
            if (s.friends.isEmpty())
                Body("A small hello can be a beginning. Add a friend in Settings.")
            s.friends.forEach { friend ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(friend.name, fontSize = 15.sp)
                        Text("Send a little butterfly", fontSize = 11.sp, color = Muted)
                    }
                    IconButton(
                        { send(friend) },
                        Modifier.semantics { contentDescription = "Reach ${friend.name}" },
                    ) {
                        SolaIcon("butterfly", Modifier.size(28.dp), Color(0xFFAE92B0))
                    }
                }
            }
        }
        GlassCard(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Eyebrow("Your care circle")
            Text(
                s.profile.clinic.ifBlank { "A place for your care team" },
                fontSize = 19.sp,
                fontWeight = FontWeight.Light,
            )
            Body("Keep your clinic details nearby, ready when you need them.")
            SoftButton("About sharing", { sharing = true }, Modifier.fillMaxWidth())
        }
        SoftButton(
            "Find support · MIELI, Finland",
            {
                runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://mieli.fi/tukea-ja-apua/kriisipuhelin?oai_link_source=model_response_hotline"
                                ),
                            )
                        )
                    }
                    .onFailure { notify("No browser is available.") }
            },
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp),
            "heart",
        )
    }
}

@Composable
fun RecapScreen(s: SolaState, store: SolaStore, back: () -> Unit) {
    var slide by rememberSaveable { mutableIntStateOf(0) }
    var intention by rememberSaveable { mutableStateOf(s.monthlyIntention) }
    val month = SolaDates.key().take(7)
    val days = s.loggedDates.filter { it.startsWith(month) }
    val notes = s.diary.filter { SolaDates.key(it.timestamp).startsWith(month) }
    val monthTasks = s.tasks.filter { SolaDates.key(it.timestamp).startsWith(month) && it.done }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(6) { index ->
                Box(
                    Modifier.weight(1f)
                        .height(3.dp)
                        .background(if (index <= slide) Ink.copy(.6f) else Color.White)
                )
            }
        }
        TopBar(back = back, label = SolaDates.format(System.currentTimeMillis(), "MMMM"))
        Spacer(Modifier.height(22.dp))
        when (slide) {
            0 -> {
                Eyebrow("A moment to look back")
                Spacer(Modifier.height(18.dp))
                Title("Your month,\nwrapped.", "A collection of small moments. All yours.")
                GardenArtwork(s, Modifier.padding(vertical = 20.dp))
                Text(
                    "${days.size} days of showing up",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Light,
                )
            }
            1 -> {
                Title("The moments\nyou kept.", "Some things deserve a little space.")
                Spacer(Modifier.height(28.dp))
                val photo = notes.lastOrNull { it.photo != null }
                if (photo != null) SolaPhoto(photo.photo, Modifier.fillMaxWidth().height(240.dp))
                GlassCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text(
                        notes.lastOrNull()?.text?.takeIf { it.isNotBlank() }
                            ?: "Your next diary entry will find a home here.",
                        fontSize = 19.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
            2 -> {
                Title("Small things.\nReal growth.", "The things you made room for.")
                GlassOrb(
                    Modifier.size(220.dp).padding(top = 20.dp),
                    Color(0xFFE2C4AB),
                    sceneTime(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SolaIcon("flower", Modifier.size(48.dp), Color(0xFFB6938F))
                        Text(
                            "${s.completedGoals.size+monthTasks.size}",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Light,
                        )
                    }
                }
                GlassCard(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    (s.completedGoals.toList() + monthTasks.map { it.title }).take(5).forEach {
                        Body("✧  $it")
                    }
                    if (s.completedGoals.isEmpty() && monthTasks.isEmpty())
                        Body("Tend an intention or mark a little win to grow a flower.")
                }
            }
            3 -> {
                Title("Every feeling\nhad a place.", "Your check-ins over the past 28 days.")
                GlassCard(Modifier.fillMaxWidth().padding(top = 30.dp)) {
                    MoodChart(s.moods, 28)
                    Body("There was room for all of it. You can explore each day in Statistics.")
                }
            }
            4 -> {
                Title("A little closer.", "The people you’ve made space for.")
                GlassOrb(Modifier.size(230.dp).padding(top = 20.dp), Lavender, sceneTime()) {
                    SolaIcon("butterfly", Modifier.size(74.dp), Color(0xFFA18AB9))
                }
                GlassCard(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    if (s.friends.isEmpty())
                        Body("Your circle can start with one person, whenever you’re ready.")
                    else
                        s.friends.forEach {
                            Text(it.name, fontSize = 19.sp, fontWeight = FontWeight.Light)
                        }
                }
            }
            5 -> {
                Title("Carry something\ngentle forward.", "An intention for the days ahead.")
                Spacer(Modifier.height(32.dp))
                SolaField(
                    "What would you like to make room for?",
                    intention,
                    { intention = it },
                    multiline = true,
                )
                Body(
                    "A wish, not a promise. You can always change it.",
                    Modifier.padding(vertical = 22.dp),
                    true,
                )
                PrimaryButton(
                    "Keep this intention",
                    {
                        store.update { it.copy(monthlyIntention = intention.trim()) }
                        back()
                    },
                    Modifier.fillMaxWidth(),
                )
                TextButton({ slide = 0 }) { Text("Replay this month", color = Muted) }
            }
        }
        Spacer(Modifier.height(30.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (slide > 0) RoundButton("back", "Previous story", { slide-- })
            else Spacer(Modifier.size(48.dp))
            Text("${slide+1} / 6", fontSize = 11.sp, color = Muted)
            if (slide < 5) RoundButton("next", "Next story", { slide++ }, true)
            else RoundButton("close", "Close recap", back)
        }
    }
}
