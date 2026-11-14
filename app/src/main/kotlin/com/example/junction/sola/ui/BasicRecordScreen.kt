package com.example.junction.sola.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.junction.sola.data.*
import java.util.Locale
import kotlin.math.roundToInt

/** Intentionally a small Settings detail, with no clinical dashboard or live cohort connection. */
@Composable
fun BasicRecordScreen(personal: SolaState, back: () -> Unit) {
    val context = LocalContext.current
    val samples = remember { runCatching { BasicSamples.load(context) }.getOrDefault(emptyList()) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var range by rememberSaveable { mutableIntStateOf(0) }
    var visible by rememberSaveable(selectedId, range) { mutableIntStateOf(6) }
    var allFeelings by rememberSaveable { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var website by remember { mutableStateOf(false) }
    val sample = samples.firstOrNull { it.id == selectedId }
    val moods = BasicRecordSummary.moods(personal, range)
    val counts = BasicRecordSummary.counts(moods)
    val days = sample?.let { BasicRecordSummary.sampleDays(it, range) }.orEmpty()
    if (picker)
        SolaDialog("A record to explore", { picker = false }) {
            ProfileChoice(
                "My record",
                if (personal.demo) "Sample garden · on this phone"
                else "Only your entries on this phone",
                sample == null,
            ) {
                selectedId = null
                picker = false
            }
            Eyebrow("Fictional samples")
            Body(
                "Small, read-only examples from the simulation website. They never become part of your journal."
            )
            samples.forEach { profile ->
                ProfileChoice(profile.name, "Demo profile", profile.id == sample?.id) {
                    selectedId = profile.id
                    picker = false
                }
            }
            if (samples.isEmpty()) Body("Sample profiles are unavailable in this build.")
        }
    if (website)
        SolaDialog("A little more detail", { website = false }) {
            Eyebrow(if (sample == null) "My record" else "${sample.name} · demo profile")
            Text(
                "The website is not available on phones yet.",
                fontSize = 20.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Light,
            )
            Body(
                if (sample == null)
                    "Your entries are saved on this phone. The detailed website is not connected to your journal yet."
                else
                    "You selected ${sample.name}’s fictional sample. A more detailed website view can be linked here later."
            )
            PrimaryButton("Back to these logs", { website = false }, Modifier.fillMaxWidth())
        }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TopBar(
            back,
            label =
                if (sample != null) "Demo profile"
                else if (personal.demo) "Sample garden" else "On this phone",
        )
        Title(
            if (sample == null) "Your record" else "${sample.name}’s record",
            "Just the basics, in one little place.",
        )
        SoftButton(
            if (sample == null) "My record · choose a view" else "${sample.name} · demo",
            { picker = true },
            Modifier.align(Alignment.CenterHorizontally),
            "stats",
        )
        if (sample != null) Body("Fictional sample data · read only", Modifier.fillMaxWidth(), true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(7 to "7 days", 28 to "28 days", 0 to "All").forEach { (value, label) ->
                ChoiceChip(label, range == value, { range = value }, Modifier.weight(1f))
            }
        }
        if (sample == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                RecordMetric("Check-ins", moods.size.toString(), Modifier.weight(1f))
                RecordMetric(
                    "Days",
                    moods.map { SolaDates.key(it.timestamp) }.distinct().size.toString(),
                    Modifier.weight(1f),
                )
                RecordMetric("Feelings", counts.size.toString(), Modifier.weight(1f))
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Eyebrow("What you’ve felt")
                if (counts.isEmpty()) Body("Your first check-in will start a little history here.")
                (if (allFeelings) counts else counts.take(4)).forEach { (label, count) ->
                    val mood = SolaContent.moods.firstOrNull { it.label.equals(label, true) }
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (mood != null) MoodFace(mood, Modifier.size(31.dp))
                            else Box(Modifier.size(10.dp).background(Sage, CircleShape))
                            Text(
                                label,
                                Modifier.weight(1f).padding(start = 10.dp),
                                fontSize = 14.sp,
                            )
                            Text(
                                "$count ${if(count==1)"time"else"times"}",
                                fontSize = 11.sp,
                                color = Muted,
                            )
                        }
                        Box(
                            Modifier.fillMaxWidth()
                                .height(4.dp)
                                .background(Sage.copy(.17f), CircleShape)
                        ) {
                            Box(
                                Modifier.fillMaxWidth(
                                        (count.toFloat() / moods.size.coerceAtLeast(1)).coerceIn(
                                            .02f,
                                            1f,
                                        )
                                    )
                                    .fillMaxHeight()
                                    .background(
                                        if (mood == null) Sage else Color(mood.color),
                                        CircleShape,
                                    )
                            )
                        }
                    }
                }
                if (counts.size > 4)
                    TextButton({ allFeelings = !allFeelings }) {
                        Text(
                            if (allFeelings) "Show less" else "Every feeling (${counts.size})",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Eyebrow("Check-in history")
                moods.take(visible).forEach { log ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SolaContent.moods
                            .firstOrNull { it.label.equals(log.label, true) }
                            ?.let {
                                MoodFace(it, Modifier.size(36.dp))
                                Spacer(Modifier.width(12.dp))
                            }
                        Column(Modifier.weight(1f)) {
                            Text(log.label, fontSize = 14.sp)
                            Text(
                                SolaDates.format(log.timestamp, "EEE, d MMM · HH:mm"),
                                fontSize = 11.sp,
                                color = Muted,
                            )
                        }
                    }
                }
                if (moods.isEmpty()) Body("No check-ins in this view yet.")
                if (moods.size > visible)
                    SoftButton(
                        "More entries (${moods.size-visible})",
                        { visible += 6 },
                        Modifier.fillMaxWidth(),
                    )
            }
        } else {
            val observed = days.filterNot { it.missing }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                RecordMetric(
                    "Mood / 5",
                    average(observed.mapNotNull { it.mood }),
                    Modifier.weight(1f),
                )
                RecordMetric(
                    "Sleep · h",
                    average(observed.mapNotNull { it.sleepHours }),
                    Modifier.weight(1f),
                )
                RecordMetric(
                    "Steps",
                    average(observed.mapNotNull { it.steps }, asSteps = true),
                    Modifier.weight(1f),
                )
            }
            Body(
                "Simple demo averages. Mood uses the website’s 1–5 scale. These are fictional entries, separate from yours.",
                centered = true,
            )
            if (days.isNotEmpty())
                Text(
                    "${shortDate(days.last().date)} – ${shortDate(days.first().date)} · ${days.size} sample days",
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            GlassCard(Modifier.fillMaxWidth()) {
                Eyebrow("Basic day logs")
                days.take(visible).forEach { day ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(shortDate(day.date), Modifier.weight(1f), fontSize = 14.sp)
                            Text(
                                if (day.missing || day.mood == null) "No mood logged"
                                else "${decimal(day.mood)} / 5",
                                fontSize = 13.sp,
                                color = Muted,
                            )
                        }
                        Body(
                            if (day.missing) "No entry for this day"
                            else
                                "${day.sleepHours?.let {"${decimal(it)} h sleep"}?:"Sleep not logged"} · ${day.steps?.let {"${steps(it)} steps"}?:"Steps not logged"}"
                        )
                    }
                    HorizontalDivider(color = Color.White.copy(.7f))
                }
                if (days.isEmpty()) Body("No sample entries in this view.")
                if (days.size > visible)
                    SoftButton(
                        "More sample days (${days.size-visible})",
                        { visible += 6 },
                        Modifier.fillMaxWidth(),
                    )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Body(
                "For more detail, check the website. Phone access is currently unavailable.",
                centered = true,
            )
            SoftButton("More on the website", { website = true }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ProfileChoice(name: String, detail: String, selected: Boolean, choose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(22.dp, selected).clickable(onClick = choose).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(35.dp).background(Lavender.copy(.24f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1), fontSize = 17.sp, fontWeight = FontWeight.Light)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, fontSize = 14.sp)
            Text(detail, color = Muted, fontSize = 10.sp)
        }
        if (selected) SolaIcon("check", Modifier.size(17.dp), Muted)
    }
}

@Composable
private fun RecordMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.glass(23.dp).padding(horizontal = 10.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.Light)
        Text(label, fontSize = 10.sp, color = Muted)
    }
}

private fun decimal(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

private fun steps(value: Double) =
    if (value >= 1000) "${decimal(value/1000)}k" else value.roundToInt().toString()

private fun average(values: List<Double>, asSteps: Boolean = false) =
    if (values.isEmpty()) "—"
    else if (asSteps) steps(values.average()) else decimal(values.average())

private fun shortDate(date: String) = SolaDates.format(SolaDates.parseKey(date), "d MMM yyyy")
