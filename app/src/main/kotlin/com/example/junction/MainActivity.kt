package com.example.junction

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import com.example.junction.dailyquiz.CheckInRoute
import com.example.junction.dailyquiz.DailyQuizScheduler
import com.example.junction.nudge.NudgeController
import com.example.junction.sola.SolaApp
import com.example.junction.sola.data.*
import com.junction.catnudge.CatNudge

class MainActivity : ComponentActivity() {
    companion object {
        @Volatile
        var checkInVisible = false
            private set
    }

    private var session by mutableIntStateOf(0)
    private var initialScreen by mutableStateOf<SolaScreen?>(null)
    private var motion by mutableStateOf(true)
    private var visibleRoute: SolaScreen? = null
    private var journal by mutableStateOf<SolaStore?>(null)
    private var store: SolaStore
        get() = checkNotNull(journal)
        set(value) {
            journal = value
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = savedInstanceState?.getInt("sola_session") ?: 0
        readLaunch(intent)
        enableEdgeToEdge()
        setContent {
            key(session) {
                SolaApp(
                    store,
                    initialScreen,
                    motion,
                    onScreenChanged = { screen ->
                        visibleRoute = screen
                        updateCheckInVisibility()
                    },
                    onSaveMood = { journal, mood ->
                        DailyQuizScheduler.logMood(this, journal, mood)
                    },
                )
            }
        }
    }

    private fun readLaunch(launch: Intent) {
        val debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val preview =
            if (debug) SolaScreen.fromPreview(launch.getStringExtra("preview_screen")) else null
        initialScreen = preview
        motion = !debug || launch.getBooleanExtra("preview_motion", true)
        store =
            SolaStore(
                this,
                if (preview != null) {
                    if (launch.getBooleanExtra("preview_demo", true)) SolaContent.demo()
                    else SolaState()
                } else null,
            )
        if (launch.action == NudgeController.ACTION_OPEN_QUESTIONS) {
            if (!launch.hasExtra(NudgeController.EXTRA_SESSION))
                launch.putExtra(
                    NudgeController.EXTRA_SESSION,
                    android.os.SystemClock.elapsedRealtimeNanos(),
                )
            if (preview == null) store.useSample(false)
            initialScreen =
                if (store.openCheckIn(CheckInRoute.fromIntent(launch))) SolaScreen.CHECK_IN
                else SolaScreen.GARDEN
        }
        if (debug && preview == null)
            launch
                .getStringExtra("bridge_url")
                ?.takeIf { TelegramGateway.validBase(it, true) }
                ?.let { url ->
                    store.update { it.copy(preferences = it.preferences.copy(backendUrl = url)) }
                }
        visibleRoute =
            initialScreen
                ?: if (store.state.value.activeCheckIn != null) SolaScreen.CHECK_IN else null
        updateCheckInVisibility()
    }

    private fun updateCheckInVisibility() {
        val visible =
            visibleRoute == SolaScreen.CHECK_IN &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (checkInVisible != visible) {
            checkInVisible = visible
            CatNudge.refresh(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updateCheckInVisibility()
    }

    override fun onPause() {
        super.onPause()
        checkInVisible = false
        CatNudge.refresh(this)
    }

    override fun onPostResume() {
        super.onPostResume()
        updateCheckInVisibility()
        CatNudge.restoreIfNeeded(this)
        DailyQuizScheduler.reschedule(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readLaunch(intent)
        session++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("sola_session", session)
        super.onSaveInstanceState(outState)
    }
}
