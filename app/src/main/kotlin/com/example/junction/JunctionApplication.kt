package com.example.junction

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import com.example.junction.dailyquiz.DailyQuizScheduler
import com.example.junction.nudge.NudgeController
import com.example.junction.sola.data.SolaStore
import com.junction.catnudge.CatNudge
import com.junction.catnudge.NudgeConfiguration

class JunctionApplication : Application() {
    private var lastRhythm: List<Any>? = null

    private fun rhythm() =
        SolaStore.personal(this).preferences.let {
            listOf(it.cadence, it.morning, it.afternoon, it.evening)
        }

    private val rhythmListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "personal" || key == null) {
            val rhythm = rhythm()
            if (rhythm != lastRhythm) {
                lastRhythm = rhythm
                DailyQuizScheduler.onRhythmChanged(this)
            }
            CatNudge.refresh(this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        CatNudge.configure(
            this,
            NudgeConfiguration(
                destinations =
                    mapOf(
                        DailyQuizScheduler.DESTINATION to
                            { context, request ->
                                Intent(context, MainActivity::class.java).apply {
                                    action = NudgeController.ACTION_OPEN_QUESTIONS
                                    putExtra(
                                        NudgeController.EXTRA_SESSION,
                                        SystemClock.elapsedRealtimeNanos(),
                                    )
                                    putExtra("nudge_id", request.id)
                                    request.payload.forEach { (key, value) -> putExtra(key, value) }
                                }
                            },
                        "home" to
                            { context, _ ->
                                Intent(context, MainActivity::class.java)
                                    .setAction(Intent.ACTION_MAIN)
                            },
                    ),
                onMonitorCheck = { DailyQuizScheduler.reconcile(it) },
                onMonitoringChanged = { context, enabled ->
                    DailyQuizScheduler.onMonitoringChanged(context, enabled)
                },
                canPresent = { _, _ -> !MainActivity.checkInVisible },
            ),
        )
        lastRhythm = rhythm()
        getSharedPreferences(SolaStore.PREFS, MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(rhythmListener)
    }
}
