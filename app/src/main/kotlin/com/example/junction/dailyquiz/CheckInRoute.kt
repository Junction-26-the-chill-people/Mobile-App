package com.example.junction.dailyquiz

import android.content.Intent
import com.example.junction.nudge.NudgeController
import com.example.junction.sola.data.CheckInSession
import com.example.junction.sola.data.SolaDates

object CheckInRoute {
    fun fromIntent(intent: Intent): CheckInSession {
        val id = intent.getStringExtra("nudge_id")?.takeIf { it.isNotBlank() } ?: "manual-preview"
        return CheckInSession(
            if (id == "manual-preview")
                "$id:${intent.getLongExtra(NudgeController.EXTRA_SESSION, 0)}"
            else id,
            intent.getStringExtra("period") ?: "preview",
            intent.getStringExtra("date")?.takeIf {
                it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            } ?: SolaDates.key(),
            intent.getStringExtra("scheduled_at")?.toLongOrNull(),
        )
    }
}
