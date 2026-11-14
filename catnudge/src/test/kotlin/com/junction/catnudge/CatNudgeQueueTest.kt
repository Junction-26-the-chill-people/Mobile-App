package com.junction.catnudge

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/** Tests the host-facing queue contract, including persistence of the chosen copy. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35])
class CatNudgeQueueTest {
    private lateinit var application: Application

    @Before fun configureHost() {
        application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("catnudge_state", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
        configure()
    }

    @Test fun stableIdsDeduplicateWithoutReplacingTheOriginalPayloadOrChosenCopy() {
        val original = NudgeRequest("daily-2026-11-14", "quiz", mapOf("day" to "2026-11-14"),
            NudgeCopy("One question?", "Please?", "I'll wait."))
        assertTrue(CatNudge.enqueue(application, original))
        CatNudge.enqueue(application, original.copy(payload = mapOf("day" to "wrong-day"),
            copy = NudgeCopy("Replacement", "Replacement plea", "Replacement final")))
        assertEquals(listOf(original), CatNudge.pendingRequests(application))
    }

    @Test fun distinctRequestsKeepTheirOrderAcrossHostReconfiguration() {
        val first = NudgeRequest("first", "quiz", copy = NudgeMessages.original)
        val second = NudgeRequest("second", "quiz", copy = NudgeCopy("Another?", "Please?", "Fine."))
        CatNudge.enqueue(application, first)
        CatNudge.enqueue(application, second)
        configure()
        assertEquals(listOf(first, second), CatNudge.pendingRequests(application))
    }

    @Test fun payloadAndCopySurviveStorageWithoutLosingUnicodeOrEscapedCharacters() {
        val request = NudgeRequest("unicode-request", "quiz",
            mapOf("question" to "A quote: \"hello\"\nand a kitten 🐈", "opaque" to "a=b&c?d"),
            NudgeCopy("A minute? 🐾", "Please?\nJust one.", "I'll sit here :(", "Let's go"),
            waitForReadyScreen = false)
        CatNudge.enqueue(application, request)
        configure()
        assertEquals(request, CatNudge.pendingRequests(application).single())
    }

    private fun configure() {
        CatNudge.configure(application, NudgeConfiguration(destinations = mapOf(
            "quiz" to { context, request ->
                Intent(context, Activity::class.java).putExtra("request_id", request.id)
            }
        )))
    }
}
