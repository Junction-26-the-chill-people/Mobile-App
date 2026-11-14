package com.example.junction.sola.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.junction.sola.data.*

@Composable
fun TodayScreen(s: SolaState, store: SolaStore, navigate: (SolaScreen) -> Unit) {
    var ideas by rememberSaveable { mutableStateOf(false) }
    var tier by rememberSaveable { mutableStateOf(s.preferences.energyTier) }
    var ideaIndex by rememberSaveable { mutableIntStateOf(0) }
    var ownIdea by rememberSaveable { mutableStateOf(false) }
    var ownText by rememberSaveable { mutableStateOf("") }
    val today = SolaDates.key()
    val task = s.tasksFor(today).lastOrNull()
    if (ideas)
        SolaDialog("A little something?", { ideas = false }) {
            Body("Choose the kind of energy you have today.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("LIGHTER", "USUAL", "BIGGER").forEach { t ->
                    ChoiceChip(
                        t,
                        t == tier,
                        {
                            tier = t
                            ideaIndex = 0
                            store.update {
                                it.copy(preferences = it.preferences.copy(energyTier = t))
                            }
                        },
                        Modifier.weight(1f),
                    )
                }
            }
            val options = SolaContent.ideas(tier, s)
            val suggestion = options[ideaIndex % options.size]
            GlassCard(Modifier.fillMaxWidth()) {
                SolaIcon("bulb", Modifier.size(26.dp), Muted)
                Text(
                    suggestion.first,
                    fontSize = 23.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Light,
                )
                Body(suggestion.second)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Shuffle", { ideaIndex++ }, Modifier.weight(1f), "replay")
                SoftButton("My own", { ownIdea = !ownIdea }, Modifier.weight(1f), "plus")
            }
            if (ownIdea) SolaField("Something that feels right", ownText, { ownText = it })
            PrimaryButton(
                "Let’s go",
                {
                    val title = if (ownIdea) ownText.trim() else suggestion.first
                    if (title.isNotEmpty()) {
                        store.update { it.copy(tasks = it.tasks + SolaTask(title = title)) }
                        ideas = false
                        ownText = ""
                    }
                },
                Modifier.fillMaxWidth(),
                !ownIdea || ownText.isNotBlank(),
            )
        }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(
            settings = { navigate(SolaScreen.SETTINGS) },
            label = if (s.demo) "Sample garden" else null,
        )
        Spacer(Modifier.height(16.dp))
        Text("Welcome back,", fontSize = 31.sp, fontWeight = FontWeight.Light, color = Ink)
        Text(
            "${s.greeting}.",
            fontSize = 36.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Light,
            color = Ink,
        )
        if (task != null) {
            Row(
                Modifier.padding(top = 18.dp).fillMaxWidth().glass(22.dp).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    {
                        store.update { st ->
                            st.copy(
                                tasks =
                                    st.tasks.map {
                                        if (it.id == task.id) it.copy(done = !it.done) else it
                                    }
                            )
                        }
                    },
                    Modifier.size(36.dp),
                ) {
                    SolaIcon(if (task.done) "check" else "flower", Modifier.size(20.dp))
                }
                Text(task.title, Modifier.weight(1f), fontSize = 12.sp, lineHeight = 17.sp)
                IconButton(
                    {
                        store.update {
                            it.copy(tasks = it.tasks.filterNot { t -> t.id == task.id })
                        }
                    },
                    Modifier.size(36.dp),
                ) {
                    SolaIcon("close", Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        GlassOrb(
            Modifier.size(250.dp)
                .semantics { contentDescription = "Check in. How are you?" }
                .clickable { navigate(SolaScreen.CHECK_IN) },
            Color(0xFFBCCDB5),
            sceneTime(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow("Check in")
                Spacer(Modifier.height(10.dp))
                Text("How are you?", fontSize = 25.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(18.dp))
                Box(Modifier.size(34.dp).glass(17.dp), contentAlignment = Alignment.Center) {
                    SolaIcon("next", Modifier.size(15.dp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (s.moodFor(today) != null)
            Text(
                "A little leaf for today. Thank you for checking in.",
                color = Muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        if (s.preferences.showIdeas)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "Are you up for something?",
                    Modifier.padding(end = 14.dp),
                    color = Muted,
                    fontSize = 13.sp,
                )
                RoundButton("bulb", "Find an idea", { ideas = true }, true)
            }
    }
}

@Composable
fun CheckInScreen(
    store: SolaStore,
    navigate: (SolaScreen) -> Unit,
    back: () -> Unit,
    saveMood: (SolaMood) -> Unit = { store.saveMood(it) },
) {
    val session = store.state.collectAsState().value.activeCheckIn
    var saved by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf("calm") }
    var level by rememberSaveable { mutableIntStateOf(3) }
    val chosenMood =
        if (mode == 0) SolaContent.moods.first { it.id == selectedId }
        else SolaContent.scaleMood(level)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(
            back,
            { navigate(SolaScreen.SETTINGS) },
            session
                ?.period
                ?.takeIf { it in listOf("morning", "afternoon", "evening") }
                ?.let { "$it check-in" },
        )
        Spacer(Modifier.height(6.dp))
        Title("How are you,\nright now?", "There’s no right answer. Just a moment for you.")
        Row(
            Modifier.padding(vertical = 10.dp).glass(24.dp).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChoiceChip("Mood meter", mode == 0, { mode = 0 })
            ChoiceChip("1–5 scale", mode == 1, { mode = 1 })
        }
        if (mode == 0) {
            SolaContent.moods.chunked(4).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    row.forEach { mood ->
                        Column(
                            Modifier.weight(1f)
                                .semantics {
                                    contentDescription = "${mood.label} mood"
                                    selected = selectedId == mood.id
                                }
                                .clickable { selectedId = mood.id },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            MoodFace(
                                mood,
                                Modifier.fillMaxWidth()
                                    .aspectRatio(1f)
                                    .padding(3.dp)
                                    .then(
                                        if (selectedId == mood.id)
                                            Modifier.border(1.4.dp, Ink.copy(.55f), CircleShape)
                                        else Modifier
                                    ),
                            )
                            Text(
                                mood.label,
                                Modifier.padding(top = 5.dp),
                                fontSize = 10.sp,
                                color = if (selectedId == mood.id) Ink else Muted,
                            )
                        }
                    }
                }
            }
        } else {
            GlassOrb(
                Modifier.size((125 + level * 17).dp).padding(vertical = 8.dp),
                Color(chosenMood.color),
                sceneTime(),
            ) {
                Text("$level", fontSize = 43.sp, fontWeight = FontWeight.Light)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (1..5).forEach { n -> ChoiceChip("$n", n == level, { level = n }) }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Body("A little low")
                Body("Feeling bright")
            }
        }
        Text(
            "Feeling ${chosenMood.label.lowercase()}.",
            Modifier.padding(top = 8.dp, bottom = 10.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
        )
        PrimaryButton(
            "Rest here",
            {
                if (!saved) {
                    saved = true
                    saveMood(chosenMood)
                    navigate(SolaScreen.GARDEN)
                }
            },
            Modifier.fillMaxWidth(),
            enabled = !saved,
        )
        Body("Each day you check in, a new branch grows.", Modifier.padding(vertical = 8.dp), true)
    }
}

@Composable
fun DiaryScreen(
    s: SolaState,
    store: SolaStore,
    back: () -> Unit,
    navigate: (SolaScreen) -> Unit,
    onPhoto: () -> Unit,
    onSpeech: () -> Unit,
) {
    var voice by rememberSaveable { mutableStateOf(false) }
    var summaryInfo by remember { mutableStateOf(false) }
    if (summaryInfo)
        SolaDialog("A little more space", { summaryInfo = false }) {
            Body(
                "Gemini summaries will live here. For now, your words are saved exactly as you write them."
            )
            PrimaryButton("Keep my full entry", { summaryInfo = false }, Modifier.fillMaxWidth())
        }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(back, { navigate(SolaScreen.SETTINGS) }, "Daily diary")
        Spacer(Modifier.height(10.dp))
        Title("Leave a little of today.", "A thought, a photo, a moment worth keeping.")
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.fillMaxWidth()
                .height(if (s.draftPhoto == null) 158.dp else 200.dp)
                .glass()
                .clickable(onClick = onPhoto),
            contentAlignment = Alignment.Center,
        ) {
            if (s.draftPhoto != null) {
                SolaPhoto(s.draftPhoto, Modifier.fillMaxSize())
                IconButton(
                    { store.update { it.copy(draftPhoto = null) } },
                    Modifier.align(Alignment.TopEnd)
                        .padding(7.dp)
                        .background(Color.White.copy(.8f), CircleShape)
                        .semantics { contentDescription = "Remove photo" },
                ) {
                    SolaIcon("close")
                }
            } else
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SolaIcon("photo", Modifier.size(27.dp), Muted)
                    Spacer(Modifier.height(10.dp))
                    Body("Add a photo")
                }
        }
        Row(Modifier.padding(vertical = 22.dp).glass(24.dp).padding(4.dp)) {
            ChoiceChip("Voice", voice, { voice = true })
            ChoiceChip("Text", !voice, { voice = false })
        }
        if (voice) {
            RoundButton(
                "mic",
                "Speak a diary entry",
                onSpeech,
                true,
                Modifier.padding(bottom = 12.dp),
            )
            Body("Tap to use your phone’s speech-to-text.", Modifier.padding(bottom = 16.dp), true)
        }
        SolaField(
            if (voice) "Your words, transcribed" else "What’s on your mind?",
            s.diaryDraft,
            { v -> store.update { it.copy(diaryDraft = v) } },
            multiline = true,
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Eyebrow("Keep the full entry")
            TextButton({ summaryInfo = true }) {
                Text("AI summary · soon", fontSize = 11.sp, color = Muted)
            }
        }
        PrimaryButton(
            "Add to today",
            { if (store.saveDiary(voice, false)) navigate(SolaScreen.GARDEN) },
            Modifier.fillMaxWidth(),
            s.diaryDraft.isNotBlank() || s.draftPhoto != null,
        )
        Body("Your diary stays on this phone.", Modifier.padding(vertical = 20.dp), true)
    }
}

@Composable
fun GardenScreen(
    s: SolaState,
    store: SolaStore,
    navigate: (SolaScreen) -> Unit,
    send: (SolaFriend) -> Unit,
    dismissButterfly: (Butterfly) -> Unit,
) {
    var replay by remember { mutableIntStateOf(0) }
    var day by rememberSaveable { mutableStateOf<String?>(null) }
    var goals by remember { mutableStateOf(false) }
    var addFriend by remember { mutableStateOf(false) }
    var task by rememberSaveable { mutableStateOf("") }
    var butterfly by remember { mutableStateOf<Butterfly?>(null) }
    if (addFriend)
        FriendEditor(null, { addFriend = false }) { friend ->
            store.update { it.copy(friends = it.friends + friend) }
            addFriend = false
        }
    day?.let { date ->
        SolaDialog(SolaDates.format(SolaDates.parseKey(date), "EEEE, d MMMM"), { day = null }) {
            s.moods
                .filter { SolaDates.key(it.timestamp) == date }
                .forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(12.dp)
                                .background(Color(SolaContent.color(m.quadrant)), CircleShape)
                        )
                        Text(
                            "${m.label} · ${SolaDates.format(m.timestamp,"HH:mm")}",
                            Modifier.padding(start = 10.dp),
                            fontSize = 13.sp,
                        )
                    }
                }
            s.notesFor(date).forEach { note ->
                if (note.photo != null)
                    SolaPhoto(note.photo, Modifier.fillMaxWidth().height(190.dp))
                if (note.text.isNotBlank()) Text(note.text, fontSize = 15.sp, lineHeight = 23.sp)
                TextButton({
                    store.update { it.copy(diary = it.diary.filterNot { n -> n.id == note.id }) }
                }) {
                    Text("Remove this entry", color = Muted, fontSize = 11.sp)
                }
            }
            s.tasksFor(date).forEach { t -> Body("${if(t.done)"✓"else"○"} ${t.title}") }
            if (s.notesFor(date).isEmpty()) Body("A small moment, kept in your garden.")
        }
    }
    butterfly?.let { b ->
        SolaDialog("From ${b.friend}", { butterfly = null }) {
            SolaIcon("butterfly", Modifier.size(45.dp), Lavender)
            Text(b.message, fontSize = 18.sp, lineHeight = 27.sp)
            PrimaryButton(
                "Keep the feeling",
                {
                    dismissButterfly(b)
                    butterfly = null
                },
                Modifier.fillMaxWidth(),
            )
        }
    }
    if (goals)
        SolaDialog("Grow a little something", { goals = false }) {
            Body("Every intention you tend adds a flower.")
            s.goals.forEach { goal ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            store.update {
                                it.copy(
                                    completedGoals =
                                        if (goal in it.completedGoals) it.completedGoals - goal
                                        else it.completedGoals + goal
                                )
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        goal in s.completedGoals,
                        { checked ->
                            store.update {
                                it.copy(
                                    completedGoals =
                                        if (checked) it.completedGoals + goal
                                        else it.completedGoals - goal
                                )
                            }
                        },
                    )
                    Text(goal, Modifier.weight(1f), fontSize = 14.sp)
                }
            }
            if (s.goals.isEmpty()) Body("Add an intention in Settings to start growing flowers.")
            HorizontalDivider(color = Sage.copy(.2f))
            Eyebrow("Today’s small wins")
            s.tasksFor(SolaDates.key()).forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        t.done,
                        { v ->
                            store.update {
                                it.copy(
                                    tasks =
                                        it.tasks.map { a ->
                                            if (a.id == t.id) a.copy(done = v) else a
                                        }
                                )
                            }
                        },
                    )
                    Text(t.title, Modifier.weight(1f), fontSize = 13.sp)
                    IconButton({
                        store.update { it.copy(tasks = it.tasks.filterNot { a -> a.id == t.id }) }
                    }) {
                        SolaIcon("trash", Modifier.size(17.dp), Muted)
                    }
                }
            }
            SolaField("A little win", task, { task = it })
            SoftButton(
                "Add to today",
                {
                    if (task.isNotBlank()) {
                        store.update {
                            it.copy(tasks = it.tasks + SolaTask(title = task.trim(), done = true))
                        }
                        task = ""
                    }
                },
                Modifier.fillMaxWidth(),
                "plus",
            )
        }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(
            settings = { navigate(SolaScreen.SETTINGS) },
            label = if (s.demo) "Sample garden" else null,
        )
        Spacer(Modifier.height(16.dp))
        Title("Your garden", "Branches grow as you log · butterflies bring people close")
        GardenArtwork(s, Modifier.padding(top = 4.dp), replay, { day = it })
        val count = s.loggedDates.count { it.startsWith(SolaDates.key().take(7)) }
        Eyebrow(SolaDates.format(System.currentTimeMillis(), "MMMM"))
        Text(
            "$count ${if(count==1)"day"else"days"} logged",
            Modifier.padding(top = 8.dp),
            fontSize = 13.sp,
            color = Muted,
        )
        Row(
            Modifier.padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SoftButton("Replay growth", { replay++ }, icon = "replay")
            SoftButton("Grow flowers", { goals = true }, icon = "flower")
        }
        if (count == 0)
            Body(
                "Your first branch begins with a check-in or diary entry.",
                Modifier.padding(bottom = 14.dp),
                true,
            )
        if (s.butterflies.isNotEmpty())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.butterflies.take(4).forEach { b ->
                    SoftButton("From ${b.friend}", { butterfly = b }, icon = "butterfly")
                }
            }
        GlassCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Your people")
                    Body("A little closer, whenever you need.")
                }
                IconButton(
                    { addFriend = true },
                    Modifier.semantics { contentDescription = "Add a friend" },
                ) {
                    SolaIcon("plus", Modifier.size(19.dp))
                }
            }
            if (s.friends.isEmpty()) Body("Add someone who feels like home.")
            s.friends.forEach { friend ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).background(Lavender.copy(.27f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(friend.name.take(1).uppercase(), fontSize = 16.sp)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(friend.name, fontSize = 14.sp)
                        Text(
                            friend.telegram.ifBlank { friend.telegramId.ifBlank { "Your person" } },
                            color = Muted,
                            fontSize = 11.sp,
                        )
                    }
                    IconButton(
                        { send(friend) },
                        Modifier.semantics {
                            contentDescription = "Send ${friend.name} a butterfly"
                        },
                    ) {
                        SolaIcon("butterfly", Modifier.size(26.dp), Color(0xFF9E8BBB))
                    }
                }
            }
        }
        SoftButton(
            "Your month, wrapped",
            { navigate(SolaScreen.RECAP) },
            Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp),
            "flower",
        )
    }
}
