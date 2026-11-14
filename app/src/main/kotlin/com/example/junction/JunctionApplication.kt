package com.example.junction

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import com.example.junction.dailyquiz.DailyQuizScheduler
import com.example.junction.nudge.NudgeController
import com.junction.catnudge.CatNudge
import com.junction.catnudge.NudgeConfiguration

class JunctionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CatNudge.configure(this, NudgeConfiguration(
            destinations = mapOf(
                DailyQuizScheduler.DESTINATION to { context, request ->
                    // Integrate the real morning/evening questionnaire here, in the host app.
                    Intent(context, MainActivity::class.java).apply {
                        action = NudgeController.ACTION_OPEN_QUESTIONS
                        putExtra(NudgeController.EXTRA_SESSION, SystemClock.elapsedRealtimeNanos())
                        putExtra("nudge_id", request.id)
                        request.payload.forEach { (key, value) -> putExtra(key, value) }
                    }
                },
                "home" to { context, _ -> Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN) },
            ),
            onMonitorCheck = { DailyQuizScheduler.reconcile(it) },
            onMonitoringChanged = { context, enabled -> DailyQuizScheduler.onMonitoringChanged(context, enabled) },
        ))
    }
}
