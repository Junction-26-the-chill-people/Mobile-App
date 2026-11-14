package com.example.junction.nudge

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.provider.Settings
import com.example.junction.JunctionApplication
import com.example.junction.dailyquiz.DailyQuizScheduler
import com.junction.catnudge.CatNudge
import com.junction.catnudge.CatNudgeService
import com.junction.catnudge.NudgeMessages
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSettings

/** Verifies the lifecycle bridge used by ADB and external foreground apps. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], application = JunctionApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class NudgeEntryPointTest {
    @Before fun clearPersistedHostState() {
        val application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("catnudge_state", Context.MODE_PRIVATE).edit().clear().commit()
        application.getSharedPreferences("daily_quiz_schedule", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun anApprovedTriggerStartsTheServiceAndFinishesItsTransientActivity() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildActivity(NudgeTriggerActivity::class.java).setup()
        try {
            val activity = controller.get()
            val service = shadowOf(activity).nextStartedService
            assertNotNull(service)
            assertEquals(CatNudgeService::class.java.name, service.component?.className)
            assertTrue(activity.isFinishing)
            val queued = CatNudge.pendingRequests(activity).single()
            assertEquals(DailyQuizScheduler.DESTINATION, queued.destination)
            assertEquals("manual-preview", queued.id)
            assertFalse(queued.waitForReadyScreen)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun adbPayloadSelectsTheHostDestinationPeriodAndStableRequestId() {
        ShadowSettings.setCanDrawOverlays(true)
        val application = RuntimeEnvironment.getApplication()
        val intent = Intent(application, NudgeTriggerActivity::class.java)
            .putExtra("destination", DailyQuizScheduler.DESTINATION)
            .putExtra("period", "evening")
            .putExtra("date", "2026-11-14")
            .putExtra("nudge_id", "adb-evening")
            .putExtra("copy", "original")
        val controller = Robolectric.buildActivity(NudgeTriggerActivity::class.java, intent).setup()
        try {
            val queued = CatNudge.pendingRequests(application).single()
            assertEquals("adb-evening", queued.id)
            assertEquals(DailyQuizScheduler.DESTINATION, queued.destination)
            assertEquals(mapOf("period" to "evening", "date" to "2026-11-14"), queued.payload)
            assertEquals(NudgeMessages.original, queued.copy)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun missingPermissionOpensSystemSettingsWithoutStartingTheService() {
        ShadowSettings.setCanDrawOverlays(false)
        val controller = Robolectric.buildActivity(NudgeTriggerActivity::class.java).setup()
        try {
            val activity = controller.get()
            val settings = shadowOf(activity).nextStartedActivity
            assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, settings.action)
            assertEquals("package:com.example.junction", settings.dataString)
            assertNull(shadowOf(activity).nextStartedService)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun theAdbDismissExtraDoesNotRequestPermissionOrCreateAnotherVisit() {
        ShadowSettings.setCanDrawOverlays(false)
        val intent = Intent(RuntimeEnvironment.getApplication(), NudgeTriggerActivity::class.java)
            .putExtra("dismiss", true)
        val controller = Robolectric.buildActivity(NudgeTriggerActivity::class.java, intent).setup()
        try {
            val activity = controller.get()
            assertTrue(activity.isFinishing)
            assertNull(shadowOf(activity).nextStartedService)
            assertNull(shadowOf(activity).nextStartedActivity)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun internalApplicationContextUsesTheTriggerActivityWithANewTask() {
        val application = RuntimeEnvironment.getApplication()
        assertTrue(NudgeController.show(application))
        val launch = shadowOf(application).nextStartedActivity
        assertEquals(NudgeTriggerActivity::class.java.name, launch.component?.className)
        assertEquals(NudgeController.ACTION_SHOW, launch.action)
        assertTrue(launch.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun activityContextKeepsItsExistingTaskWhenSendingANudge() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            assertTrue(NudgeController.show(controller.get()))
            val launch = shadowOf(controller.get()).nextStartedActivity
            assertEquals(0, launch.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            assertEquals(NudgeTriggerActivity::class.java.name, launch.component?.className)
        } finally { controller.pause().stop().destroy() }
    }
}
