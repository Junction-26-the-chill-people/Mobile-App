package com.junction.catnudge

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.junction.catnudge.visual.NudgeOverlayView
import java.io.FileDescriptor
import java.io.PrintWriter

/** One user-enabled host owns both screen eligibility and its interactive overlay. */
class CatNudgeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val readiness = ScreenReadiness()
    private var overlay: NudgeOverlayView? = null
    private var displayedId: String? = null
    private var receiverRegistered = false
    private var foregroundStarted = false
    private var destroyed = false
    private var refreshing = false
    private var otherAppSeen = false
    private var lastEligibilityStart: Long? = null
    private var homePackages: Set<String> = emptySet()
    private val pendingRefresh = Runnable { refreshNow(false) }
    private val readyCheck = Runnable { refreshNow(true) }
    private val appOpenCheck = Runnable { refreshNow(false) }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == Intent.ACTION_SCREEN_OFF) {
                readiness.reset()
                otherAppSeen = false
                lastEligibilityStart = null
                handler.removeCallbacks(readyCheck)
                handler.removeCallbacks(appOpenCheck)
                pauseOverlay()
                updateNotification()
            } else {
                refreshNow(action == Intent.ACTION_TIME_TICK || action == Intent.ACTION_TIME_CHANGED ||
                    action == Intent.ACTION_TIMEZONE_CHANGED)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        CatNudge.runningService = this
        // Process recreation restores a resting visit only after a fresh eligible-screen delay.
        NudgeStore.entries(this).filter { it.hasShown }.forEach { entry ->
            NudgeStore.update(this, entry.request.id) { it.copy(needsReady = true) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        @Suppress("DEPRECATION")
        homePackages = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).map { it.activityInfo.packageName }.toSet()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CatNudge.runningService = this
        when (intent?.action) {
            ACTION_DISMISS -> CatNudge.dismiss(this)
            ACTION_DISABLE_MONITOR -> CatNudge.stopMonitoring(this)
        }
        if (!shouldRun()) { stopHost(); return START_NOT_STICKY }
        try {
            val notification = CatNudgeNotifications.foreground(this)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(CatNudgeNotifications.FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(CatNudgeNotifications.FOREGROUND_ID, notification)
            }
            foregroundStarted = true
            CatNudgeNotifications.cancelResume(this)
            refreshNow(true)
        } catch (error: RuntimeException) {
            Log.e(CatNudge.TAG, "Unable to start cat host", error)
            CatNudgeNotifications.postResume(this)
            stopHost()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    internal fun requestRefresh() {
        if (destroyed) return
        handler.removeCallbacks(pendingRefresh)
        // Always post: enqueue calls made by the host's monitor callback cannot recurse.
        handler.post(pendingRefresh)
    }

    private fun refreshNow(checkMonitor: Boolean) {
        if (destroyed || !foregroundStarted || refreshing) return
        refreshing = true
        try {
            if (checkMonitor && CatNudge.isMonitoringEnabled(this)) {
                CatNudge.invokeSafely("monitor check") { CatNudge.configuration?.onMonitorCheck?.invoke(applicationContext) }
            }
            if (!shouldRun()) { stopHost(); return }
            val interactive = getSystemService(PowerManager::class.java).isInteractive
            val unlocked = !getSystemService(KeyguardManager::class.java).isKeyguardLocked
            readiness.update(interactive, unlocked, SystemClock.elapsedRealtime(), System.currentTimeMillis())
            if (readiness.sinceElapsed != lastEligibilityStart) {
                lastEligibilityStart = readiness.sinceElapsed
                otherAppSeen = false
            }
            handler.removeCallbacks(readyCheck)
            handler.removeCallbacks(appOpenCheck)
            if (!interactive || !unlocked) {
                pauseOverlay()
                updateNotification()
                return
            }
            val remaining = readiness.remainingMillis(SystemClock.elapsedRealtime())
            val head = NudgeStore.entries(this).firstOrNull()
            if (overlay != null && displayedId != head?.request?.id) removeOverlay()
            if (remaining > 0 && (CatNudge.isMonitoringEnabled(this) || head?.needsReady == true)) {
                handler.postDelayed(readyCheck, remaining)
            }
            if (head == null) { updateNotification(); return }
            if (overlay != null) { updateNotification(); return }
            if (!CatNudge.canDrawOverlays(this)) {
                CatNudgeNotifications.postResume(this)
                updateNotification()
                return
            }
            if (head.needsReady && remaining > 0) { updateNotification(); return }
            val useAppGate = head.needsReady && CatNudge.isAppOpenGateEnabled(this) && CatNudge.hasUsageAccess(this)
            if (useAppGate && !otherAppSeen) {
                otherAppSeen = hasAnotherAppOpened()
                if (!otherAppSeen) {
                    handler.postDelayed(appOpenCheck, APP_OPEN_POLL_MS)
                    updateNotification()
                    return
                }
            }
            showOverlay(head)
            updateNotification()
        } catch (error: RuntimeException) {
            Log.e(CatNudge.TAG, "Could not refresh cat visit", error)
            CatNudgeNotifications.postResume(this)
        } finally {
            refreshing = false
        }
    }

    private fun hasAnotherAppOpened(): Boolean {
        return try {
            if (readiness.sinceWallTime == 0L) return false
            val events = getSystemService(UsageStatsManager::class.java)
                .queryEvents(readiness.sinceWallTime, System.currentTimeMillis()) ?: return false
            val event = UsageEvents.Event()
            var found = false
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                    event.packageName != packageName && event.packageName != "android" && event.packageName !in homePackages) {
                    found = true
                    break
                }
            }
            // Retain only this boolean in memory; app identities/history are never stored.
            found
        } catch (error: RuntimeException) {
            Log.w(CatNudge.TAG, "Usage Access unavailable; using screen readiness", error)
            // A disappearing optional grant must not make the base feature unusable.
            true
        }
    }

    private fun showOverlay(entry: StoredNudge) {
        val request = entry.request
        val view = NudgeOverlayView(this,
            onAccept = { accept(request.id) },
            onDismiss = { finishRequest(request.id, NudgeOutcome.DISMISSED) },
            copy = request.copy,
            initialOutsideTaps = entry.outsideTaps,
            onOutsideTapsChanged = { taps -> NudgeStore.update(this, request.id) { it.copy(outsideTaps = taps.coerceIn(0, 2)) } },
        )
        overlay = view
        displayedId = request.id
        @Suppress("DEPRECATION")
        val windowType = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val parameters = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; setTitle("Cat nudge invitation") }
        try {
            getSystemService(WindowManager::class.java).addView(view, parameters)
            NudgeStore.update(this, request.id) { it.copy(hasShown = true, needsReady = false) }
            view.start(resume = entry.hasShown)
        } catch (error: RuntimeException) {
            removeOverlay()
            Log.e(CatNudge.TAG, "Could not attach cat overlay", error)
            CatNudgeNotifications.postResume(this)
        }
    }

    private fun accept(id: String) {
        val request = NudgeStore.entries(this).firstOrNull { it.request.id == id }?.request ?: return
        val route = CatNudge.configuration?.destinations?.get(request.destination)
        if (route == null) {
            Log.w(CatNudge.TAG, "The host has not configured the requested destination")
            Toast.makeText(this, "This invitation's destination is not available yet.", Toast.LENGTH_LONG).show()
            // Accept has stopped the view's animation. Restore an interactive copy, retaining state.
            removeOverlay()
            requestRefresh()
            return
        }
        try {
            val destination = route(applicationContext, request).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Launch while the overlay remains visible, then consume the request exactly once.
            startActivity(destination)
            finishRequest(id, NudgeOutcome.ACCEPTED)
        } catch (error: RuntimeException) {
            Log.e(CatNudge.TAG, "Could not open the invitation destination", error)
            Toast.makeText(this, "Open the app to continue this invitation.", Toast.LENGTH_LONG).show()
            removeOverlay()
            requestRefresh()
        }
    }

    private fun finishRequest(id: String, outcome: NudgeOutcome) {
        val removed = NudgeStore.remove(this, id) ?: return
        if (displayedId == id) removeOverlay()
        CatNudge.reportOutcome(this, removed, outcome)
        requestRefresh()
    }

    private fun pauseOverlay() {
        val id = displayedId ?: NudgeStore.entries(this).firstOrNull()?.request?.id
        if (id != null) NudgeStore.update(this, id) { it.copy(needsReady = true) }
        removeOverlay()
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        overlay = null
        displayedId = null
        view.dispose()
        // The ViewRoot may be registered before the first attached-to-window traversal.
        try { getSystemService(WindowManager::class.java).removeViewImmediate(view) }
        catch (_: IllegalArgumentException) { /* Already removed, or addView failed. */ }
    }

    private fun updateNotification() {
        if (!foregroundStarted || destroyed) return
        try { getSystemService(NotificationManager::class.java).notify(CatNudgeNotifications.FOREGROUND_ID, CatNudgeNotifications.foreground(this)) }
        catch (error: RuntimeException) { Log.w(CatNudge.TAG, "Could not update cat notification", error) }
    }

    private fun shouldRun() = CatNudge.isMonitoringEnabled(this) || NudgeStore.entries(this).isNotEmpty()

    private fun stopHost() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        if (foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        if (CatNudge.runningService === this) CatNudge.runningService = null
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        pauseOverlay()
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        receiverRegistered = false
        if (CatNudge.runningService === this) CatNudge.runningService = null
        super.onDestroy()
    }

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        writer.println("catVisitActive=${overlay != null}")
        writer.println("catPendingCount=${NudgeStore.entries(this).size}")
        writer.println("catMonitoringEnabled=${CatNudge.isMonitoringEnabled(this)}")
    }

    companion object {
        internal const val ACTION_DISMISS = "com.junction.catnudge.action.DISMISS"
        internal const val ACTION_DISABLE_MONITOR = "com.junction.catnudge.action.DISABLE_MONITOR"
        private const val APP_OPEN_POLL_MS = 2_000L
    }
}
