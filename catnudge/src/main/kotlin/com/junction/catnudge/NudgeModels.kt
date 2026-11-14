package com.junction.catnudge

import android.content.Context
import android.content.Intent
import kotlin.random.Random

/** One invitation and its two follow-ups. Selected once and retained across screen-off/restarts. */
data class NudgeCopy(
    val invitation: String,
    val gentlePlea: String,
    val finalPlea: String,
    val acceptLabel: String = "Yes",
)

/** Applications may provide their own copy without changing the renderer or service. */
object NudgeMessages {
    val invitations = listOf(
        "answer a question or two?",
        "can you answer a question or two?",
        "do you have a minute?",
        "can you spare a second?",
    )
    val gentlePleas = listOf("Please? :(", "Pretty please? :(", "Just a tiny moment? :(")
    val finalPleas = listOf("fine :(", "okay... I'll wait :(", "I'll just sit here then :(")

    fun choose(random: Random = Random.Default) = NudgeCopy(
        invitations.random(random), gentlePleas.random(random), finalPleas.random(random)
    )

    val original = NudgeCopy(invitations.first(), gentlePleas.first(), finalPleas.first())
}

/** Use stable IDs for scheduled requests so retries cannot create duplicate visits. */
data class NudgeRequest(
    val id: String,
    val destination: String,
    val payload: Map<String, String> = emptyMap(),
    val copy: NudgeCopy = NudgeMessages.choose(),
    val waitForReadyScreen: Boolean = true,
)

enum class NudgeOutcome { ACCEPTED, DISMISSED }

/** Install this once from Application.onCreate; destination routing belongs to the host app. */
data class NudgeConfiguration(
    val destinations: Map<String, (Context, NudgeRequest) -> Intent>,
    val onOutcome: (Context, NudgeRequest, NudgeOutcome) -> Unit = { _, _, _ -> },
    val onMonitorCheck: (Context) -> Unit = {},
    val onMonitoringChanged: (Context, Boolean) -> Unit = { _, _ -> },
    /** Temporarily defer presentation while the host is editing a check-in, without consuming it. */
    val canPresent: (Context, NudgeRequest) -> Boolean = { _, _ -> true },
)
