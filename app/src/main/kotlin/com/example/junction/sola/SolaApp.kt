package com.example.junction.sola

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.junction.sola.data.*
import com.example.junction.sola.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Feature entry point. Navigation is intentionally independent of the cat widget's destination
 * registry.
 */
@Composable
fun SolaApp(
    store: SolaStore,
    initialScreen: SolaScreen? = null,
    motion: Boolean = true,
    onScreenChanged: (SolaScreen) -> Unit = {},
    onSaveMood: (SolaStore, SolaMood) -> Unit = { journal, mood -> journal.saveMood(mood) },
) {
    val s by store.state.collectAsState()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val scope = rememberCoroutineScope()
    var screenName by rememberSaveable {
        mutableStateOf(
            (initialScreen
                    ?: if (s.activeCheckIn != null) SolaScreen.CHECK_IN
                    else if (s.onboardingComplete) SolaScreen.TODAY else SolaScreen.WELCOME)
                .name
        )
    }
    val screen = SolaScreen.valueOf(screenName)
    SideEffect { onScreenChanged(screen) }
    var history by rememberSaveable { mutableStateOf(listOf<String>()) }
    var bridgeStatus by remember {
        mutableStateOf(
            if (s.preferences.backendUrl.isBlank()) "Not connected" else "Not checked yet"
        )
    }
    var selectedFriend by remember { mutableStateOf<SolaFriend?>(null) }
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }
    fun navigate(to: SolaScreen) {
        if (to == screen) return
        if (
            to in
                listOf(
                    SolaScreen.SETTINGS,
                    SolaScreen.CHECK_IN,
                    SolaScreen.RECAP,
                    SolaScreen.BASIC_LOGS,
                )
        )
            history = history + screenName
        else history = emptyList()
        screenName = to.name
    }
    fun back() {
        screenName =
            when {
                history.isNotEmpty() -> history.last().also { history = history.dropLast(1) }
                screen.ordinal in 1..6 -> SolaScreen.entries[screen.ordinal - 1].name
                else -> SolaScreen.TODAY.name
            }
    }
    LaunchedEffect(s.activeCheckIn?.id) {
        if (s.activeCheckIn != null && screen != SolaScreen.CHECK_IN) navigate(SolaScreen.CHECK_IN)
    }
    fun gateway() = TelegramGateway(s.preferences.backendUrl, debug)
    fun openBot() {
        if (s.demo) {
            notify("Return to your own journal to connect Telegram.")
            return
        }
        scope.launch {
            try {
                val username =
                    gateway().state().optJSONObject("botInfo")?.optString("botUsername").orEmpty()
                check(username.matches(Regex("[A-Za-z0-9_]+"))) {
                    "The bridge has not connected to its bot yet."
                }
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/$username"))
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notify(e.message ?: "Could not open the bot chat.")
            }
        }
    }
    fun checkBridge() {
        if (s.demo) {
            bridgeStatus = "Leave sample mode to connect Telegram"
            return
        }
        scope.launch {
            bridgeStatus = "Connecting…"
            try {
                val json = gateway().state()
                bridgeStatus = TelegramGateway.connectionStatus(json)
                store.update { it.copy(butterflies = gateway().butterflies(json)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                bridgeStatus = e.message ?: "Could not connect"
            }
        }
    }
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        val photo = SolaMedia.importPhoto(context, uri)
                        store.update { it.copy(draftPhoto = photo) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        notify(e.message ?: "Could not save this photo.")
                    }
                }
        }
    val speech =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { text ->
                        store.update {
                            it.copy(
                                diaryDraft =
                                    listOf(it.diaryDraft, text)
                                        .filter { part -> part.isNotBlank() }
                                        .joinToString("\n")
                            )
                        }
                    }
            }
        }
    BackHandler(enabled = screen != SolaScreen.TODAY && screen != SolaScreen.WELCOME) { back() }
    LaunchedEffect(s.preferences.backendUrl, s.demo) {
        if (s.demo || !TelegramGateway.validBase(s.preferences.backendUrl, debug))
            return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    val json = gateway().state()
                    store.update {
                        val butterflies = gateway().butterflies(json)
                        if (it.butterflies == butterflies) it
                        else it.copy(butterflies = butterflies)
                    }
                    bridgeStatus = TelegramGateway.connectionStatus(json)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    bridgeStatus = "Bridge unavailable"
                }
                delay(30_000)
            }
        }
    }
    SolaTheme(motion) {
        val tone =
            when (screen) {
                SolaScreen.STATISTICS,
                SolaScreen.SUPPORT -> "rose"
                SolaScreen.DIARY -> "dawn"
                SolaScreen.RECAP -> "lavender"
                SolaScreen.WELCOME,
                SolaScreen.ACCOUNT,
                SolaScreen.FRIEND -> "warm"
                else -> "sage"
            }
        AmbientSurface(tone) {
            Column(
                Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .widthIn(max = 600.dp)
                    .align(Alignment.TopCenter)
            ) {
                Box(Modifier.weight(1f)) {
                    when (screen) {
                        SolaScreen.WELCOME,
                        SolaScreen.ACCOUNT,
                        SolaScreen.INTERESTS,
                        SolaScreen.GOALS,
                        SolaScreen.CONNECT,
                        SolaScreen.RHYTHM,
                        SolaScreen.FRIEND -> OnboardingScreen(screen, s, store, ::navigate, ::back)
                        SolaScreen.TODAY -> TodayScreen(s, store, ::navigate)
                        SolaScreen.CHECK_IN ->
                            CheckInScreen(store, ::navigate, ::back, { onSaveMood(store, it) })
                        SolaScreen.GARDEN ->
                            GardenScreen(
                                s,
                                store,
                                ::navigate,
                                {
                                    if (s.demo)
                                        notify(
                                            "Return to your own journal before sending a real butterfly."
                                        )
                                    else selectedFriend = it
                                },
                                { b ->
                                    scope.launch {
                                        try {
                                            if (s.preferences.backendUrl.isNotBlank())
                                                gateway().dismiss(b.id)
                                            store.update {
                                                it.copy(
                                                    butterflies =
                                                        it.butterflies.filterNot { item ->
                                                            item.id == b.id
                                                        }
                                                )
                                            }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            notify(e.message ?: "Could not dismiss this butterfly.")
                                        }
                                    }
                                },
                            )
                        SolaScreen.DIARY ->
                            DiaryScreen(
                                s,
                                store,
                                ::back,
                                ::navigate,
                                {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                {
                                    try {
                                        speech.launch(
                                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                                .putExtra(
                                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                                )
                                                .putExtra(
                                                    RecognizerIntent.EXTRA_PROMPT,
                                                    "A little of today…",
                                                )
                                        )
                                    } catch (_: android.content.ActivityNotFoundException) {
                                        notify(
                                            "Speech input is unavailable on this phone. You can type your entry instead."
                                        )
                                    }
                                },
                            )
                        SolaScreen.STATISTICS -> StatisticsScreen(s, ::navigate)
                        SolaScreen.SUPPORT ->
                            SupportScreen(
                                s,
                                ::navigate,
                                {
                                    if (s.demo)
                                        notify(
                                            "Return to your own journal before sending a real butterfly."
                                        )
                                    else selectedFriend = it
                                },
                                ::notify,
                            )
                        SolaScreen.SETTINGS ->
                            SettingsScreen(
                                s,
                                store,
                                ::back,
                                ::navigate,
                                bridgeStatus,
                                ::checkBridge,
                                ::openBot,
                            )
                        SolaScreen.RECAP -> RecapScreen(s, store, ::back)
                        SolaScreen.BASIC_LOGS ->
                            BasicRecordScreen(
                                if (store.isPreview || !s.demo) s else SolaStore.personal(context),
                                ::back,
                            )
                    }
                }
                if (
                    screen in
                        listOf(
                            SolaScreen.TODAY,
                            SolaScreen.GARDEN,
                            SolaScreen.DIARY,
                            SolaScreen.STATISTICS,
                            SolaScreen.SUPPORT,
                        )
                )
                    BottomNavigation(screen, ::navigate)
            }
            SnackbarHost(
                snackbar,
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 100.dp, start = 16.dp, end = 16.dp),
            )
        }
        selectedFriend?.let { friend ->
            SolaDialog("A butterfly for ${friend.name}", { if (!busy) selectedFriend = null }) {
                Body("A small message to say you’d love some company. No questions asked.")
                if (s.preferences.backendUrl.isNotBlank()) {
                    Body(
                        "Send through your Telegram bot. A new contact may also receive an invitation first."
                    )
                    PrimaryButton(
                        if (busy) "Sending…" else "Send butterfly",
                        {
                            busy = true
                            scope.launch {
                                try {
                                    val api = gateway()
                                    val remote = api.state().optJSONArray("friends")
                                    val existing =
                                        (0 until (remote?.length() ?: 0))
                                            .mapNotNull { remote?.optJSONObject(it) }
                                            .firstOrNull {
                                                it.optString("telegramChatId") ==
                                                    friend.telegramId &&
                                                    friend.telegramId.isNotBlank()
                                            }
                                    api.profile(s.profile)
                                    val id = existing?.getString("id") ?: api.registerFriend(friend)
                                    val receipt = api.sendButterfly(id)
                                    selectedFriend = null
                                    notify(receipt)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    notify(e.message ?: "Telegram could not deliver the butterfly.")
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        Modifier.fillMaxWidth(),
                        !busy,
                    )
                } else
                    Body(
                        "The bot bridge isn’t connected. You can open a message in Telegram and choose the recipient yourself."
                    )
                SoftButton(
                    "Open Telegram composer",
                    {
                        val text =
                            "${s.greeting} sent you a butterfly 🦋 — I’d love to have some company, no questions asked."
                        val intent =
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, text)
                                .setPackage("org.telegram.messenger")
                        try {
                            context.startActivity(intent)
                            selectedFriend = null
                        } catch (_: android.content.ActivityNotFoundException) {
                            notify(
                                "Telegram is not installed. Connect the bot bridge in Settings instead."
                            )
                        }
                    },
                    Modifier.fillMaxWidth(),
                    "send",
                )
            }
        }
    }
}

@Composable
private fun BottomNavigation(current: SolaScreen, navigate: (SolaScreen) -> Unit) {
    val tabs =
        listOf(
            Triple(SolaScreen.TODAY, "sun", "Today"),
            Triple(SolaScreen.GARDEN, "flower", "Garden"),
            Triple(SolaScreen.DIARY, "plus", "Diary"),
            Triple(SolaScreen.STATISTICS, "stats", "Statistics"),
            Triple(SolaScreen.SUPPORT, "heart", "Support"),
        )
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp, top = 4.dp)
            .fillMaxWidth()
            .glass(32.dp)
            .height(64.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        tabs.forEach { (screen, icon, label) ->
            val active = current == screen
            Column(
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .semantics {
                        contentDescription = label
                        selected = active
                    }
                    .clickable { navigate(screen) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (screen == SolaScreen.DIARY)
                    Box(
                        Modifier.size(43.dp).background(Ink, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        SolaIcon("plus", Modifier.size(22.dp), Color.White)
                    }
                else {
                    SolaIcon(icon, Modifier.size(22.dp), if (active) Ink else Muted.copy(.55f))
                    if (active)
                        Box(Modifier.padding(top = 5.dp).size(3.dp).background(Ink, CircleShape))
                }
            }
        }
    }
}
