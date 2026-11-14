package com.example.junction

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.junction.nudge.NudgeController
import com.example.junction.sola.SolaApp
import com.example.junction.sola.data.*
import com.example.junction.ui.nudge.ReservedScreen

class MainActivity : ComponentActivity() {
    private var questionsOpen by mutableStateOf(false)
    private var period by mutableStateOf("preview")
    private var session by mutableIntStateOf(0)
    private var previewScreen by mutableStateOf<SolaScreen?>(null)
    private var motion by mutableStateOf(true)
    private lateinit var store: SolaStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = savedInstanceState?.getInt("sola_session") ?: 0
        readLaunch(intent)
        questionsOpen = savedInstanceState?.getBoolean("questions_open") ?: questionsOpen
        period = savedInstanceState?.getString("quiz_period") ?: period
        enableEdgeToEdge()
        setContent {
            BackHandler(enabled = questionsOpen) { questionsOpen = false }
            if (questionsOpen) ReservedScreen(onBack = { questionsOpen = false }, period = period)
            else key(session) { SolaApp(store, previewScreen, motion) }
        }
    }

    private fun readLaunch(launch: Intent) {
        questionsOpen = launch.action == NudgeController.ACTION_OPEN_QUESTIONS
        period = launch.getStringExtra("period") ?: "preview"
        val debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        previewScreen =
            if (debug) SolaScreen.fromPreview(launch.getStringExtra("preview_screen")) else null
        motion = !debug || launch.getBooleanExtra("preview_motion", true)
        store =
            SolaStore(
                this,
                if (previewScreen != null) {
                    if (launch.getBooleanExtra("preview_demo", true)) SolaContent.demo()
                    else SolaState()
                } else null,
            )
        if (debug && previewScreen == null)
            launch
                .getStringExtra("bridge_url")
                ?.takeIf { TelegramGateway.validBase(it, true) }
                ?.let { url ->
                    store.update { it.copy(preferences = it.preferences.copy(backendUrl = url)) }
                }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readLaunch(intent)
        session++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("sola_session", session)
        outState.putBoolean("questions_open", questionsOpen)
        outState.putString("quiz_period", period)
        super.onSaveInstanceState(outState)
    }
}
