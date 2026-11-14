package com.example.junction.sola.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.junction.sola.data.*

@Composable
fun OnboardingScreen(
    screen: SolaScreen,
    state: SolaState,
    store: SolaStore,
    navigate: (SolaScreen) -> Unit,
    back: () -> Unit,
) {
    val step =
        listOf(
                SolaScreen.WELCOME,
                SolaScreen.ACCOUNT,
                SolaScreen.INTERESTS,
                SolaScreen.GOALS,
                SolaScreen.CONNECT,
                SolaScreen.RHYTHM,
                SolaScreen.FRIEND,
            )
            .indexOf(screen)
    var customDialog by remember { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf("") }
    var friendName by rememberSaveable { mutableStateOf("") }
    var friendId by rememberSaveable { mutableStateOf("") }
    var friendHandle by rememberSaveable { mutableStateOf("") }
    val next = { navigate(SolaScreen.entries[step + 1]) }
    if (customDialog)
        SolaDialog(
            if (screen == SolaScreen.INTERESTS) "Something you love" else "Your gentle intention",
            { customDialog = false },
        ) {
            SolaField("Write your own", custom, { custom = it })
            PrimaryButton(
                "Add",
                {
                    val value = custom.trim()
                    if (value.isNotEmpty()) {
                        store.update {
                            if (screen == SolaScreen.INTERESTS)
                                it.copy(
                                    interests = (it.interests + value).distinct(),
                                    customInterests = (it.customInterests + value).distinct(),
                                )
                            else
                                it.copy(
                                    goals = (it.goals + value).distinct(),
                                    customGoals = (it.customGoals + value).distinct(),
                                )
                        }
                        custom = ""
                        customDialog = false
                    }
                },
                Modifier.fillMaxWidth(),
                custom.isNotBlank(),
            )
        }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(
            if (step > 0) back else null,
            label = if (step > 0) "Sola · ${step} of 6" else "Sola",
        )
        when (screen) {
            SolaScreen.WELCOME -> {
                val welcomeGarden = remember { SolaContent.demo() }
                Spacer(Modifier.height(28.dp))
                Text(
                    "hello",
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Light,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = (-5).sp,
                )
                Body("thanks for dropping by", centered = true)
                Box(Modifier.height(290.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GlassOrb(Modifier.size(245.dp), Color(0xFFCBD7C0), sceneTime()) {
                        GardenArtwork(welcomeGarden, Modifier.width(200.dp).padding(top = 20.dp))
                    }
                }
                Title(
                    "A little space for you.",
                    "Check in, notice the small things,\nand watch your garden grow.",
                )
                Spacer(Modifier.height(30.dp))
                PrimaryButton("Begin", { navigate(SolaScreen.ACCOUNT) }, Modifier.fillMaxWidth())
                TextButton({
                    store.update { it.copy(onboardingComplete = true) }
                    navigate(SolaScreen.TODAY)
                }) {
                    Text("Skip to home", color = Muted)
                }
                TextButton({
                    store.useSample(true)
                    navigate(SolaScreen.TODAY)
                }) {
                    Text("Explore the sample garden", fontSize = 12.sp, color = Muted)
                }
            }
            SolaScreen.ACCOUNT -> {
                Spacer(Modifier.height(28.dp))
                Title("Come on in.", "Make this little space your own.")
                Spacer(Modifier.height(32.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SolaField(
                        "Your name",
                        state.profile.name,
                        { name -> store.update { it.copy(profile = it.profile.copy(name = name)) } },
                    )
                    SolaField(
                        "Email (optional)",
                        state.profile.email,
                        { v -> store.update { it.copy(profile = it.profile.copy(email = v)) } },
                        keyboard = KeyboardType.Email,
                    )
                    SolaField(
                        "Telegram User ID (optional)",
                        state.profile.telegramId,
                        { v ->
                            store.update {
                                it.copy(
                                    profile =
                                        it.profile.copy(
                                            telegramId = v.filter { c -> c.isDigit() || c == '-' }
                                        )
                                )
                            }
                        },
                        keyboard = KeyboardType.Number,
                    )
                    SolaField(
                        "Telegram handle (optional)",
                        state.profile.telegram,
                        { v -> store.update { it.copy(profile = it.profile.copy(telegram = v)) } },
                    )
                }
                Body(
                    "Your profile is saved on this phone. Account sign-in and Google connection will come later.",
                    Modifier.padding(vertical = 20.dp),
                    true,
                )
                PrimaryButton(
                    "Continue",
                    next,
                    Modifier.fillMaxWidth(),
                    state.profile.name.isNotBlank(),
                )
            }
            SolaScreen.INTERESTS -> {
                Spacer(Modifier.height(28.dp))
                Title("What brings you light?", "Choose the little things that feel like you.")
                Spacer(Modifier.height(32.dp))
                val choices = (SolaContent.interests + state.customInterests).distinct()
                choices.chunked(2).forEachIndexed { row, items ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        items.forEachIndexed { index, item ->
                            val selected = item in state.interests
                            Column(
                                Modifier.widthIn(max = 145.dp).clickable {
                                    store.update {
                                        it.copy(
                                            interests =
                                                if (selected) it.interests - item
                                                else it.interests + item
                                        )
                                    }
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                GlassOrb(
                                    Modifier.size(if ((row + index) % 2 == 0) 112.dp else 98.dp)
                                        .then(
                                            if (selected) Modifier.border(1.dp, Sage, CircleShape)
                                            else Modifier
                                        ),
                                    listOf(Sage, Lavender, Color(0xFFE5C5AB))[(row + index) % 3],
                                ) {
                                    if (selected) SolaIcon("check", Modifier.size(22.dp))
                                }
                                Text(
                                    item,
                                    Modifier.padding(top = 7.dp),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                TextButton({ customDialog = true }) { Text("+ Something else", color = Muted) }
                PrimaryButton("Continue", next, Modifier.fillMaxWidth().padding(top = 20.dp))
            }
            SolaScreen.GOALS -> {
                Spacer(Modifier.height(28.dp))
                Title("Gentle intentions.", "Small steps. Nothing to prove.")
                Spacer(Modifier.height(28.dp))
                (SolaContent.goals + state.customGoals).distinct().forEach { goal ->
                    val chosen = goal in state.goals
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .glass(22.dp, chosen)
                            .clickable {
                                store.update {
                                    it.copy(
                                        goals = if (chosen) it.goals - goal else it.goals + goal
                                    )
                                }
                            }
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SolaIcon("flower", Modifier.size(20.dp), if (chosen) Ink else Sage)
                        Text(
                            goal,
                            Modifier.weight(1f).padding(horizontal = 13.dp),
                            fontSize = 14.sp,
                        )
                        if (chosen) SolaIcon("check", Modifier.size(17.dp))
                    }
                }
                TextButton({ customDialog = true }) { Text("+ My own intention", color = Muted) }
                PrimaryButton("Continue", next, Modifier.fillMaxWidth().padding(top = 20.dp))
            }
            SolaScreen.CONNECT -> {
                Spacer(Modifier.height(32.dp))
                Title("Quietly beside you.", "Choose what you might like to connect later.")
                Spacer(Modifier.height(46.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("watch" to "Watch", "phone" to "Phone", "music" to "Music").forEach {
                        (icon, label) ->
                        val selected = label in state.preferences.selectedSources
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlassOrb(
                                Modifier.size(96.dp).clickable {
                                    store.update {
                                        it.copy(
                                            preferences =
                                                it.preferences.copy(
                                                    selectedSources =
                                                        if (selected)
                                                            it.preferences.selectedSources - label
                                                        else it.preferences.selectedSources + label
                                                )
                                        )
                                    }
                                },
                                if (selected) Sage else Lavender,
                            ) {
                                SolaIcon(icon, Modifier.size(28.dp))
                            }
                            Text(label, fontSize = 13.sp)
                            if (selected) Text("Selected", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
                Body(
                    "These are preferences for future integrations. No watch, activity or music data is being collected.",
                    Modifier.padding(vertical = 48.dp),
                    true,
                )
                PrimaryButton("Continue", next, Modifier.fillMaxWidth())
                TextButton(next) { Text("Maybe later", color = Muted) }
            }
            SolaScreen.RHYTHM -> {
                Spacer(Modifier.height(28.dp))
                Title("When should we check in?", "A rhythm that leaves room for life.")
                Spacer(Modifier.height(28.dp))
                RhythmControls(state.preferences) { p -> store.update { it.copy(preferences = p) } }
                Body(
                    "Your rhythm is saved. Phone reminders will be connected in the next widget integration step.",
                    Modifier.padding(vertical = 20.dp),
                    true,
                )
                PrimaryButton("Continue", next, Modifier.fillMaxWidth())
            }
            SolaScreen.FRIEND -> {
                Spacer(Modifier.height(28.dp))
                Title("Who’s in your corner?", "A familiar face. A gentle connection.")
                Spacer(Modifier.height(24.dp))
                GlassOrb(Modifier.size(130.dp), Lavender, sceneTime()) {
                    SolaIcon("butterfly", Modifier.size(40.dp), Color(0xFF9A8DAB))
                }
                Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SolaField("Their name", friendName, { friendName = it })
                    SolaField(
                        "Telegram User ID (optional)",
                        friendId,
                        { friendId = it.filter { c -> c.isDigit() || c == '-' } },
                        keyboard = KeyboardType.Number,
                    )
                    SolaField("Telegram handle (optional)", friendHandle, { friendHandle = it })
                }
                Body(
                    "Adding a person here saves them locally. You choose when to reach out.",
                    Modifier.padding(vertical = 20.dp),
                    true,
                )
                PrimaryButton(
                    "Let’s begin",
                    {
                        store.update {
                            it.copy(
                                onboardingComplete = true,
                                friends =
                                    if (friendName.isBlank()) it.friends
                                    else
                                        it.friends +
                                            SolaFriend(
                                                name = friendName.trim(),
                                                telegramId = friendId.trim(),
                                                telegram = friendHandle.trim(),
                                            ),
                            )
                        }
                        navigate(SolaScreen.TODAY)
                    },
                    Modifier.fillMaxWidth(),
                )
                TextButton({
                    store.update { it.copy(onboardingComplete = true) }
                    navigate(SolaScreen.TODAY)
                }) {
                    Text("I’ll add someone later", color = Muted)
                }
            }
            else -> Unit
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun RhythmControls(p: SolaPreferences, update: (SolaPreferences) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Every day", "Weekdays", "Every 2 days").forEach { label ->
                ChoiceChip(
                    label,
                    p.cadence == label,
                    { update(p.copy(cadence = label)) },
                    Modifier.weight(1f),
                )
            }
        }
        listOf("Morning" to p.morning, "Afternoon" to p.afternoon, "Evening" to p.evening)
            .forEach { (label, window) ->
                fun changed(w: CheckInWindow) {
                    update(
                        when (label) {
                            "Morning" -> p.copy(morning = w)
                            "Afternoon" -> p.copy(afternoon = w)
                            else -> p.copy(evening = w)
                        }
                    )
                }
                Row(
                    Modifier.fillMaxWidth().glass(24.dp).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, fontSize = 14.sp)
                        Text(
                            if (window.count == 0) "No check-ins"
                            else "${window.count} check-in${if(window.count==1)"" else "s"}",
                            fontSize = 11.sp,
                            color = Muted,
                        )
                    }
                    IconButton(
                        { changed(window.copy(count = (window.count - 1).coerceAtLeast(0))) },
                        Modifier.size(36.dp),
                    ) {
                        Text("−", fontSize = 22.sp)
                    }
                    Text("${window.count}", fontSize = 14.sp)
                    IconButton(
                        { changed(window.copy(count = (window.count + 1).coerceAtMost(3))) },
                        Modifier.size(36.dp),
                    ) {
                        SolaIcon("plus", Modifier.size(14.dp))
                    }
                    TextButton({
                        val parts = window.time.split(':')
                        TimePickerDialog(
                                context,
                                { _, h, m ->
                                    changed(
                                        window.copy(
                                            time =
                                                String.format(
                                                    java.util.Locale.ROOT,
                                                    "%02d:%02d",
                                                    h,
                                                    m,
                                                )
                                        )
                                    )
                                },
                                parts[0].toInt(),
                                parts[1].toInt(),
                                true,
                            )
                            .show()
                    }) {
                        Text(window.time, fontSize = 13.sp)
                    }
                }
            }
    }
}
