package com.junction.catnudge

import android.app.Activity
import android.app.AppOpsManager
import android.app.Application
import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.view.WindowManager
import com.junction.catnudge.visual.NudgeOverlayView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.util.concurrent.TimeUnit

/** Runs the real service, durable queue, screen gate, and native overlay as one unit. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class CatNudgeServiceTest {
    private lateinit var application: Application
    private lateinit var service: CatNudgeService
    private var controller: ServiceController<CatNudgeService>? = null
    private val outcomes = mutableListOf<Pair<NudgeRequest, NudgeOutcome>>()

    @Before fun configureHost() {
        application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("catnudge_state", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
        shadowOf(application).grantPermissions(application.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        shadowOf(application.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        shadowOf(application.getSystemService(PowerManager::class.java)).turnScreenOn(true)
        AndroidAnimationClock.setScale(1f)
        CatNudge.configure(application, NudgeConfiguration(
            destinations = mapOf("quiz" to { context, request ->
                Intent(context, Activity::class.java).setAction("host.quiz")
                    .putExtra("request_id", request.id)
                    .putExtra("period", request.payload["period"])
                    .putExtra("date", request.payload["date"])
            }),
            onOutcome = { _, request, outcome -> outcomes.add(request to outcome) },
        ))
    }

    @After fun destroyHost() {
        controller?.destroy()
        controller = null
    }

    @Test fun aManualVisitBypassesReadinessAndOwnsOneTouchableSpecialUseWindow() {
        assertEquals(Service.START_STICKY, start(manual()))
        idle(100)
        val view = overlays().single()
        assertEquals("ARRIVING", view.visitState)
        val params = view.layoutParams as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.height)
        assertEquals(0, params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, service.foregroundServiceType)
        assertNotNull(shadowOf(service).lastForegroundNotification)
    }

    @Test fun hostEditingGateDefersTheOverlayWithoutConsumingItsRequest() {
        var allowed=false
        CatNudge.configure(application,CatNudge.configuration!!.copy(canPresent={_,_->allowed}))
        start(manual());idle(100)
        assertTrue(overlays().isEmpty());assertEquals(1,CatNudge.pendingRequests(application).size)
        allowed=true;CatNudge.refresh(application);idle(100)
        assertEquals(1,overlays().size)
    }

    @Test fun cancellingACompletedHostVisitRetainsOtherPendingInvitations() {
        CatNudge.enqueue(application,manual().copy(id="first"))
        CatNudge.enqueue(application,manual().copy(id="second"))
        assertTrue(CatNudge.dismiss(application,"first"))
        assertFalse(CatNudge.dismiss(application,"first"))
        assertEquals(listOf("second"),CatNudge.pendingRequests(application).map {it.id})
    }

    @Test fun anAutomaticVisitWaitsForFifteenContinuousSecondsOnAnUnlockedScreen() {
        start(automatic())
        idle(14_999)
        assertTrue(overlays().isEmpty())
        idle(100)
        assertEquals(1, overlays().size)
        assertEquals("ARRIVING", overlays().single().visitState)
    }

    @Test fun aScreenLockRestartsTheReadinessDelayInsteadOfCountingLockedTime() {
        start(automatic())
        idle(10_000)
        shadowOf(application.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        application.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        idle(1000)
        shadowOf(application.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        application.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        idle(14_999)
        assertTrue(overlays().isEmpty())
        idle(100)
        assertEquals(1, overlays().size)
    }

    @Test fun screenOffAndServiceRecreationPreserveTheRequestCopyAndFinalPlea() {
        val request = manual()
        start(request)
        idle(3500)
        overlays().single().performClick()
        idle(850)
        overlays().single().performClick()
        idle(850)
        assertEquals("FAREWELL", overlays().single().visitState)
        shadowOf(application.getSystemService(PowerManager::class.java)).turnScreenOn(false)
        idle(100)
        assertTrue(overlays().isEmpty())
        assertEquals(listOf(request), CatNudge.pendingRequests(application))
        assertEquals(2, NudgeStore.entries(application).single().outsideTaps)
        assertTrue(outcomes.isEmpty())
        controller?.destroy()
        controller = null

        shadowOf(application.getSystemService(PowerManager::class.java)).turnScreenOn(true)
        start()
        idle(14_999)
        assertTrue(overlays().isEmpty())
        idle(100)
        val resumed = overlays().single()
        assertEquals("FAREWELL", resumed.visitState)
        assertEquals(request.copy.finalPlea, resumed.messageView.text.toString())
        assertEquals(listOf(request), CatNudge.pendingRequests(application))
        assertTrue(outcomes.isEmpty())
        resumed.performClick()
        idle(1000)
        assertTrue(CatNudge.pendingRequests(application).isEmpty())
        assertEquals(listOf(request to NudgeOutcome.DISMISSED), outcomes)
    }

    @Test fun repeatingTheSameStableIdKeepsTheCurrentWindowAndInteractionProgress() {
        val request = manual()
        start(request)
        idle(3500)
        val original = overlays().single()
        original.performClick()
        idle(850)
        CatNudge.enqueue(application, request.copy(copy = NudgeCopy("Replacement", "Replacement", "Replacement")))
        service.onStartCommand(Intent(service, CatNudgeService::class.java), 0, 2)
        idle(100)
        assertSame(original, overlays().single())
        assertEquals(request.copy.gentlePlea, overlays().single().messageView.text.toString())
        assertEquals(1, NudgeStore.entries(application).single().outsideTaps)
        assertEquals(listOf(request), CatNudge.pendingRequests(application))
    }

    @Test fun aFinalPleaPersistsBeyondFiveMinutesUntilTheUserDismissesIt() {
        val request = manual()
        start(request)
        idle(3500)
        overlays().single().performClick()
        idle(850)
        overlays().single().performClick()
        idle(850)
        shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.MINUTES)
        assertEquals("FAREWELL", overlays().single().visitState)
        assertEquals(request.copy.finalPlea, overlays().single().messageView.text.toString())
        assertFalse(shadowOf(service).isStoppedBySelf)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        overlays().single().performClick()
        idle(1000)
        assertTrue(overlays().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertNull(shadowOf(service).lastForegroundNotification)
    }

    @Test fun acceptanceRoutesTheOpaquePayloadThroughTheConfiguredHostDestination() {
        val request = manual().copy(payload = mapOf("period" to "evening", "date" to "2026-11-14"))
        start(request)
        idle(3500)
        overlays().single().yesButton.performClick()
        idle(100)
        val launch = shadowOf(service).nextStartedActivity
        assertEquals("host.quiz", launch.action)
        assertEquals(request.id, launch.getStringExtra("request_id"))
        assertEquals("evening", launch.getStringExtra("period"))
        assertEquals("2026-11-14", launch.getStringExtra("date"))
        assertTrue(launch.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(CatNudge.pendingRequests(application).isEmpty())
        assertEquals(listOf(request to NudgeOutcome.ACCEPTED), outcomes)
        assertTrue(overlays().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test fun aMissingHostDestinationKeepsTheInvitationAvailableInsteadOfConsumingIt() {
        val request = manual().copy(destination = "not-configured")
        start(request)
        idle(3500)
        overlays().single().yesButton.performClick()
        idle(100)
        assertEquals(listOf(request), CatNudge.pendingRequests(application))
        assertEquals("READY", overlays().single().visitState)
        assertTrue(outcomes.isEmpty())
        assertNull(shadowOf(service).nextStartedActivity)
    }

    @Test fun deniedOptionalUsageAccessLeavesTheNormalReadinessPathUsable() {
        val appOps = application.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), application.packageName, AppOpsManager.MODE_IGNORED)
        assertFalse(CatNudge.hasUsageAccess(application))
        CatNudge.setAppOpenGateEnabled(application, true)
        assertFalse(CatNudge.isAppOpenGateEnabled(application))
        start(automatic())
        idle(15_100)
        assertEquals(1, overlays().size)
    }

    @Test fun aManualVisitBypassesAnEnabledAppOpenGateWithoutRequiringAnotherAppEvent() {
        val appOps = application.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), application.packageName, AppOpsManager.MODE_ALLOWED)
        CatNudge.setAppOpenGateEnabled(application, true)
        assertTrue(CatNudge.isAppOpenGateEnabled(application))
        start(manual())
        idle(100)
        assertEquals(1, overlays().size)
    }

    @Test fun missingOverlayPermissionPreservesTheQueueForLaterRecovery() {
        ShadowSettings.setCanDrawOverlays(false)
        val request = automatic()
        start(request)
        idle(15_100)
        assertTrue(overlays().isEmpty())
        assertEquals(listOf(request), CatNudge.pendingRequests(application))
        assertTrue(outcomes.isEmpty())
        ShadowSettings.setCanDrawOverlays(true)
        CatNudge.refresh(application)
        idle(100)
        assertEquals(1, overlays().size)
    }

    @Test fun disablingMonitoringStopsAnIdleHostWithoutCreatingAnInvitation() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        try {
            assertTrue(CatNudge.startMonitoring(activity.get()))
            AndroidAnimationClock.setScale(1f)
            start()
            assertTrue(CatNudge.isMonitoringEnabled(application))
            assertNotNull(shadowOf(service).lastForegroundNotification)
            CatNudge.stopMonitoring(application)
            idle(100)
            assertFalse(CatNudge.isMonitoringEnabled(application))
            assertTrue(CatNudge.pendingRequests(application).isEmpty())
            assertTrue(shadowOf(service).isStoppedBySelf)
            assertNull(shadowOf(service).lastForegroundNotification)
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun anEmptyDisabledHostDoesNotLeaveAnIdleForegroundService() {
        assertEquals(Service.START_NOT_STICKY, start())
        assertTrue(overlays().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertNull(shadowOf(service).lastForegroundNotification)
    }

    @Test fun emergencyDismissalConsumesAllQueuedRequestsAndRemovesTheWindow() {
        val first = manual()
        val second = manual().copy(id = "second")
        CatNudge.enqueue(application, first)
        CatNudge.enqueue(application, second)
        start()
        service.onStartCommand(Intent(service, CatNudgeService::class.java).setAction(CatNudgeService.ACTION_DISMISS), 0, 2)
        idle(100)
        assertTrue(CatNudge.pendingRequests(application).isEmpty())
        assertTrue(overlays().isEmpty())
        assertEquals(listOf(first to NudgeOutcome.DISMISSED, second to NudgeOutcome.DISMISSED), outcomes)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test fun aForegroundStartFailurePreservesTheQueuedRequestForRecovery() {
        val request = manual()
        CatNudge.enqueue(application, request)
        createService()
        shadowOf(service).setThrowInStartForeground(SecurityException("blocked by test"))
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(Intent(service, CatNudgeService::class.java), 0, 1))
        assertEquals(listOf(request), CatNudge.pendingRequests(application))
        assertTrue(overlays().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test @Config(sdk = [25]) fun androidNougatUsesTheLegacyOverlayWindowType() {
        start(manual())
        idle(100)
        @Suppress("DEPRECATION")
        assertEquals(WindowManager.LayoutParams.TYPE_PHONE, (overlays().single().layoutParams as WindowManager.LayoutParams).type)
    }

    private fun manual() = NudgeRequest("manual-visit", "quiz", copy = NudgeMessages.original, waitForReadyScreen = false)
    private fun automatic() = NudgeRequest("daily-visit", "quiz", copy = NudgeMessages.original)

    private fun createService() {
        controller = Robolectric.buildService(CatNudgeService::class.java)
        controller!!.create()
        service = controller!!.get()
    }

    private fun start(request: NudgeRequest? = null): Int {
        request?.let { assertTrue(CatNudge.enqueue(application, it)) }
        createService()
        return service.onStartCommand(Intent(service, CatNudgeService::class.java), 0, 1)
    }

    private fun overlays(): List<NudgeOverlayView> =
        Shadow.extract<ShadowWindowManagerImpl>(application.getSystemService(WindowManager::class.java))
            .views.filterIsInstance<NudgeOverlayView>()

    private fun idle(ms: Long) = AndroidAnimationClock.advance(ms)
}
