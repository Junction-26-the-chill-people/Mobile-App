package com.example.junction.sola.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.example.junction.sola.data.*

@Composable
fun FriendEditor(friend: SolaFriend?, dismiss: () -> Unit, save: (SolaFriend) -> Unit) {
    var name by rememberSaveable(friend?.id) { mutableStateOf(friend?.name.orEmpty()) }
    var id by rememberSaveable(friend?.id) { mutableStateOf(friend?.telegramId.orEmpty()) }
    var handle by rememberSaveable(friend?.id) { mutableStateOf(friend?.telegram.orEmpty()) }
    var phone by rememberSaveable(friend?.id) { mutableStateOf(friend?.phone.orEmpty()) }
    SolaDialog(if (friend == null) "Someone in your corner" else "Your person", dismiss) {
        SolaField("Name", name, { name = it })
        SolaField(
            "Telegram User ID (optional)",
            id,
            { id = it.filter { c -> c.isDigit() || c == '-' } },
            keyboard = KeyboardType.Number,
        )
        SolaField("Telegram handle (optional)", handle, { handle = it })
        SolaField("Phone number (optional)", phone, { phone = it }, keyboard = KeyboardType.Phone)
        Body("Saved on this phone. A butterfly is sent only when you choose Send.")
        PrimaryButton(
            "Save person",
            {
                val cleanHandle = handle.trim()
                save(
                    (friend ?: SolaFriend(name = name.trim())).copy(
                        name = name.trim(),
                        telegramId = id.trim(),
                        telegram =
                            if (cleanHandle.isNotBlank() && !cleanHandle.startsWith('@'))
                                "@$cleanHandle"
                            else cleanHandle,
                        phone = phone.trim(),
                    )
                )
            },
            Modifier.fillMaxWidth(),
            name.isNotBlank() && (id.isBlank() || id.matches(Regex("-?[0-9]+"))),
        )
    }
}

@Composable
private fun SettingsLink(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Light)
            Text(detail, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
        }
        SolaIcon("next", Modifier.size(15.dp), Muted)
    }
}

@Composable
fun ToggleRow(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 14.sp)
            if (detail.isNotBlank())
                Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        }
        Switch(checked, change)
    }
}

@Composable
fun SettingsScreen(
    s: SolaState,
    store: SolaStore,
    back: () -> Unit,
    navigate: (SolaScreen) -> Unit,
    bridgeStatus: String,
    checkBridge: () -> Unit,
    openBot: () -> Unit,
) {
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var editFriend by remember { mutableStateOf<SolaFriend?>(null) }
    var friendEditor by remember { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf("") }
    if (friendEditor)
        FriendEditor(editFriend, { friendEditor = false }) { friend ->
            store.update {
                it.copy(
                    friends =
                        if (editFriend == null) it.friends + friend
                        else it.friends.map { f -> if (f.id == friend.id) friend else f }
                )
            }
            friendEditor = false
        }
    when (panel) {
        "profile" ->
            SolaDialog("A little about you", { panel = null }) {
                SolaField(
                    "Name",
                    s.profile.name,
                    { v -> store.update { it.copy(profile = it.profile.copy(name = v)) } },
                )
                SolaField(
                    "Email",
                    s.profile.email,
                    { v -> store.update { it.copy(profile = it.profile.copy(email = v)) } },
                    keyboard = KeyboardType.Email,
                )
                SolaField(
                    "Telegram User ID",
                    s.profile.telegramId,
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
                    "Telegram handle",
                    s.profile.telegram,
                    { v -> store.update { it.copy(profile = it.profile.copy(telegram = v)) } },
                )
                SolaField(
                    "Clinic name (optional)",
                    s.profile.clinic,
                    { v -> store.update { it.copy(profile = it.profile.copy(clinic = v)) } },
                )
                SolaField(
                    "Clinic phone (optional)",
                    s.profile.clinicPhone,
                    { v -> store.update { it.copy(profile = it.profile.copy(clinicPhone = v)) } },
                    keyboard = KeyboardType.Phone,
                )
                ToggleRow(
                    "I’d like to share with my clinician",
                    "Preference only; no clinic is connected yet.",
                    s.profile.clinicianSharing,
                ) { v ->
                    store.update { it.copy(profile = it.profile.copy(clinicianSharing = v)) }
                }
                PrimaryButton("Done", { panel = null }, Modifier.fillMaxWidth())
            }
        "rhythm" ->
            SolaDialog("Your gentle rhythm", { panel = null }) {
                RhythmControls(s.preferences) { v -> store.update { it.copy(preferences = v) } }
                Body(
                    "Saved preferences. Scheduled invitations will be connected when the cat widget is integrated."
                )
                PrimaryButton("Done", { panel = null }, Modifier.fillMaxWidth())
            }
        "ideas" ->
            SolaDialog("Little possibilities", { panel = null }) {
                Eyebrow("Default energy")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("LIGHTER", "USUAL", "BIGGER").forEach { v ->
                        ChoiceChip(
                            v,
                            s.preferences.energyTier == v,
                            {
                                store.update {
                                    it.copy(preferences = it.preferences.copy(energyTier = v))
                                }
                            },
                            Modifier.weight(1f),
                        )
                    }
                }
                Eyebrow("A direction")
                listOf(
                        "balanced" to "A little of everything",
                        "gentle-solo" to "Quiet time for myself",
                        "outdoors" to "A breath of fresh air",
                        "social" to "A little connection",
                        "creative" to "Make something",
                    )
                    .forEach { (value, label) ->
                        ChoiceChip(
                            label,
                            s.preferences.ideaDirection == value,
                            {
                                store.update {
                                    it.copy(
                                        preferences = it.preferences.copy(ideaDirection = value)
                                    )
                                }
                            },
                            Modifier.fillMaxWidth(),
                        )
                    }
                ToggleRow(
                    "Include friends",
                    "Suggestions can invite a little company.",
                    s.preferences.includeFriends,
                ) { v ->
                    store.update { it.copy(preferences = it.preferences.copy(includeFriends = v)) }
                }
                ToggleRow(
                    "Show ideas on Today",
                    "A small prompt, when you feel like it.",
                    s.preferences.showIdeas,
                ) { v ->
                    store.update { it.copy(preferences = it.preferences.copy(showIdeas = v)) }
                }
            }
        "interests",
        "goals" ->
            SolaDialog(
                if (panel == "interests") "What brings you light?" else "Gentle intentions",
                { panel = null },
            ) {
                val interests = panel == "interests"
                val choices =
                    if (interests) (SolaContent.interests + s.customInterests).distinct()
                    else (SolaContent.goals + s.customGoals).distinct()
                val selected = if (interests) s.interests else s.goals
                choices.forEach { value ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                store.update {
                                    if (interests)
                                        it.copy(
                                            interests =
                                                if (value in it.interests) it.interests - value
                                                else it.interests + value
                                        )
                                    else
                                        it.copy(
                                            goals =
                                                if (value in it.goals) it.goals - value
                                                else it.goals + value
                                        )
                                }
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            value in selected,
                            { checked ->
                                store.update {
                                    if (interests)
                                        it.copy(
                                            interests =
                                                if (checked) (it.interests + value).distinct()
                                                else it.interests - value
                                        )
                                    else
                                        it.copy(
                                            goals =
                                                if (checked) (it.goals + value).distinct()
                                                else it.goals - value
                                        )
                                }
                            },
                        )
                        Text(value, Modifier.weight(1f), fontSize = 13.sp)
                        if (value in (if (interests) s.customInterests else s.customGoals))
                            IconButton({
                                store.update {
                                    if (interests)
                                        it.copy(
                                            customInterests = it.customInterests - value,
                                            interests = it.interests - value,
                                        )
                                    else
                                        it.copy(
                                            customGoals = it.customGoals - value,
                                            goals = it.goals - value,
                                            completedGoals = it.completedGoals - value,
                                        )
                                }
                            }) {
                                SolaIcon("trash", Modifier.size(16.dp), Muted)
                            }
                    }
                }
                SolaField("Something of your own", custom, { custom = it })
                SoftButton(
                    "Add",
                    {
                        val v = custom.trim()
                        if (v.isNotEmpty()) {
                            store.update {
                                if (interests)
                                    it.copy(
                                        customInterests = (it.customInterests + v).distinct(),
                                        interests = (it.interests + v).distinct(),
                                    )
                                else
                                    it.copy(
                                        customGoals = (it.customGoals + v).distinct(),
                                        goals = (it.goals + v).distinct(),
                                    )
                            }
                            custom = ""
                        }
                    },
                    Modifier.fillMaxWidth(),
                    "plus",
                )
            }
        "friends" ->
            SolaDialog("Your people", { panel = null }) {
                s.friends.forEach { friend ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clickable {
                                editFriend = friend
                                friendEditor = true
                            }
                        ) {
                            Text(friend.name, fontSize = 15.sp)
                            Text(
                                friend.telegram.ifBlank {
                                    friend.phone.ifBlank { "Add contact details" }
                                },
                                fontSize = 11.sp,
                                color = Muted,
                            )
                        }
                        IconButton({
                            store.update {
                                it.copy(friends = it.friends.filterNot { f -> f.id == friend.id })
                            }
                        }) {
                            SolaIcon("trash", Modifier.size(17.dp), Muted)
                        }
                    }
                }
                SoftButton(
                    "Add someone",
                    {
                        editFriend = null
                        friendEditor = true
                    },
                    Modifier.fillMaxWidth(),
                    "plus",
                )
            }
        "sources" ->
            SolaDialog("Quietly beside you", { panel = null }) {
                listOf("Watch", "Phone", "Music").forEach { source ->
                    ToggleRow(
                        source,
                        "Integration planned",
                        source in s.preferences.selectedSources,
                    ) { checked ->
                        store.update {
                            it.copy(
                                preferences =
                                    it.preferences.copy(
                                        selectedSources =
                                            if (checked) it.preferences.selectedSources + source
                                            else it.preferences.selectedSources - source
                                    )
                            )
                        }
                    }
                }
                Body("These preferences do not grant permissions or connect a data source yet.")
            }
        "telegram" ->
            SolaDialog("Telegram connection", { panel = null }) {
                Body(
                    "The local bridge keeps the bot token in its .env file. Only friends and butterfly messages are exchanged; your diary stays on your phone."
                )
                SolaField(
                    "Bridge URL",
                    s.preferences.backendUrl,
                    { v ->
                        store.update {
                            it.copy(preferences = it.preferences.copy(backendUrl = v.trim()))
                        }
                    },
                    keyboard = KeyboardType.Uri,
                )
                Body(bridgeStatus)
                SoftButton("Check connection", checkBridge, Modifier.fillMaxWidth(), "replay")
                SoftButton("Open bot chat", openBot, Modifier.fillMaxWidth(), "send")
                Body(
                    "You and your friend must press Start in the bot chat first. The bot replies with your numeric User ID; add yours to your profile and theirs to their contact."
                )
                Text(
                    "USB development: http://127.0.0.1:3001 with ADB reverse. Production needs HTTPS and an authenticated, per-user bridge.",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = Muted,
                )
            }
        "reset" ->
            SolaDialog(
                if (s.demo) "Restart the sample garden?" else "Clear this phone’s journal?",
                { panel = null },
            ) {
                Body(
                    if (s.demo)
                        "The sample garden will return to its original five days. Your personal journal is separate."
                    else
                        "This removes your local profile, entries, preferences and people. This cannot be undone."
                )
                PrimaryButton(
                    if (s.demo) "Reset sample" else "Clear local data",
                    {
                        store.resetCurrent()
                        panel = null
                        navigate(if (s.demo) SolaScreen.TODAY else SolaScreen.WELCOME)
                    },
                    Modifier.fillMaxWidth(),
                )
                SoftButton("Keep everything", { panel = null }, Modifier.fillMaxWidth())
            }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState())
    ) {
        TopBar(back, label = if (s.demo) "Sample mode" else null)
        Spacer(Modifier.height(12.dp))
        Title("Make yourself at home.", "Your space, your pace.")
        Spacer(Modifier.height(26.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Eyebrow("You")
            SettingsLink(
                s.profile.name.ifBlank { "Your profile" },
                "Profile, Telegram & care circle",
            ) {
                panel = "profile"
            }
            SettingsLink(
                "Your people",
                "${s.friends.size} ${if(s.friends.size==1)"person"else"people"} in your corner",
            ) {
                panel = "friends"
            }
        }
        GlassCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Eyebrow("A little rhythm")
            SettingsLink("Check-in preferences", s.preferences.cadence) { panel = "rhythm" }
            SettingsLink(
                "Ideas for your day",
                "${s.preferences.energyTier.lowercase()} energy · ${s.preferences.ideaDirection}",
            ) {
                panel = "ideas"
            }
        }
        GlassCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Eyebrow("The things that matter")
            SettingsLink("Your interests", "${s.interests.size} little sources of light") {
                panel = "interests"
            }
            SettingsLink("Your intentions", "${s.goals.size} gentle steps") { panel = "goals" }
            SettingsLink("Connected sources", "Watch, phone & music · coming later") {
                panel = "sources"
            }
        }
        GlassCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Eyebrow("Connections")
            SettingsLink("Telegram butterflies", bridgeStatus) { panel = "telegram" }
            Body("Gemini text summaries & reflections are reserved for a later integration.")
        }
        GlassCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Eyebrow("This little space")
            SettingsLink("Revisit the welcome", "Your saved journal will stay here") {
                navigate(SolaScreen.WELCOME)
            }
            SettingsLink(
                if (s.demo) "Return to my journal" else "Explore the sample garden",
                if (s.demo) "Keep sample entries separate" else "A five-day example, with Maya",
            ) {
                store.useSample(!s.demo)
                navigate(
                    if (store.state.value.onboardingComplete) SolaScreen.TODAY
                    else SolaScreen.WELCOME
                )
            }
            SettingsLink(
                if (s.demo) "Reset sample data" else "Clear local data",
                "You’ll be asked before anything is removed",
            ) {
                panel = "reset"
            }
        }
        Body(
            "Sola · a little room to grow",
            Modifier.fillMaxWidth().padding(vertical = 28.dp),
            true,
        )
    }
}
