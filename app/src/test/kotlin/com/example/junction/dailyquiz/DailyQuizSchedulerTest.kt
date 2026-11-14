package com.example.junction.dailyquiz

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import com.example.junction.JunctionApplication
import com.junction.catnudge.CatNudge
import com.junction.catnudge.NudgeConfiguration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSettings
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/** Tests host policy against the real reusable library queue. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], application = JunctionApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DailyQuizSchedulerTest {
    private lateinit var application: Application
    private lateinit var activity: ActivityController<Activity>
    private lateinit var originalZone: TimeZone

    @Before fun enableHostMonitoring() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Bucharest"))
        application = RuntimeEnvironment.getApplication()
        for (file in listOf("catnudge_state", "daily_quiz_schedule")) {
            application.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
        }
        // Keep this test's policy clock explicit; Application's real-time monitor hook is tested by the host bridge.
        CatNudge.configure(application, NudgeConfiguration(destinations = mapOf(
            DailyQuizScheduler.DESTINATION to { context, _ -> Intent(context, Activity::class.java) }
        )))
        ShadowSettings.setCanDrawOverlays(true)
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        assertTrue(CatNudge.startMonitoring(activity.get()))
    }

    @After fun closeHost() {
        CatNudge.stopMonitoring(application)
        activity.pause().stop().destroy()
        TimeZone.setDefault(originalZone)
    }

    @Test fun repeatedMorningReconciliationIssuesExactlyOneDurableSlot() {
        val now = at(2026, 11, 14, 10)
        DailyQuizScheduler.reconcile(application, now)
        val first = CatNudge.pendingRequests(application).single()
        repeat(3) { DailyQuizScheduler.reconcile(application, now + 60_000) }
        assertEquals(listOf(first), CatNudge.pendingRequests(application))
        assertEquals("daily-quiz:2026-11-14:morning", first.id)
        assertEquals("daily_quiz", first.destination)
        assertEquals(mapOf("period" to "morning", "date" to "2026-11-14", "scheduled_at" to now.toString()), first.payload)
        assertTrue(first.waitForReadyScreen)
    }

    @Test fun eveningReconciliationQueuesBothDuePeriodsWithDifferentRoutingPayloads() {
        val now = at(2026, 11, 14, 21)
        DailyQuizScheduler.reconcile(application, now)
        DailyQuizScheduler.reconcile(application, now)
        val requests = CatNudge.pendingRequests(application)
        assertEquals(listOf("daily-quiz:2026-11-14:morning", "daily-quiz:2026-11-14:evening"), requests.map { it.id })
        assertEquals(listOf("morning", "evening"), requests.map { it.payload["period"] })
        assertEquals(listOf(at(2026, 11, 14, 10), at(2026, 11, 14, 21)), requests.map { it.payload.getValue("scheduled_at").toLong() })
    }

    @Test fun nextDaySlotsDoNotReplaceUnansweredRequestsFromThePreviousDay() {
        DailyQuizScheduler.reconcile(application, at(2026, 11, 14, 21))
        val unanswered = CatNudge.pendingRequests(application)
        DailyQuizScheduler.reconcile(application, at(2026, 11, 15, 10))
        val pending = CatNudge.pendingRequests(application)
        assertEquals(unanswered, pending.take(2))
        assertEquals("daily-quiz:2026-11-15:morning", pending.last().id)
        assertEquals(3, pending.size)
    }

    @Test fun disabledMonitoringDoesNotIssueDailyInvitations() {
        CatNudge.stopMonitoring(application)
        DailyQuizScheduler.reconcile(application, at(2026, 11, 14, 21))
        assertTrue(CatNudge.pendingRequests(application).isEmpty())
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int) =
        LocalDateTime.of(year, month, day, hour, 0).atZone(ZoneId.of("Europe/Bucharest")).toInstant().toEpochMilli()
}
