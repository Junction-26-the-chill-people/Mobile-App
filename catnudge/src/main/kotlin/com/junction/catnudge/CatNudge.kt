package com.junction.catnudge

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast

/** Reusable overlay runtime. Configure routing in the host Application before starting a visit. */
object CatNudge {
    internal const val TAG = "CatNudge"
    @Volatile internal var configuration: NudgeConfiguration? = null
        private set
    @Volatile internal var runningService: CatNudgeService? = null

    fun configure(context: Context, configuration: NudgeConfiguration) {
        this.configuration = configuration
        refresh(context)
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Call from a resumed, visible Activity. Missing overlay permission is never silently granted. */
    fun show(activity: Activity, request: NudgeRequest): Boolean {
        if (!canDrawOverlays(activity)) {
            Toast.makeText(activity, "Allow display over other apps before sending the cat.", Toast.LENGTH_LONG).show()
            return false
        }
        if (!queue(activity, request, notifyIfPaused = false)) return false
        return startHost(activity)
    }

    /** Durable and idempotent. Never starts a foreground service from a background caller. */
    fun enqueue(context: Context, request: NudgeRequest): Boolean = queue(context, request, notifyIfPaused = true)

    private fun queue(context: Context, request: NudgeRequest, notifyIfPaused: Boolean): Boolean {
        if (request.id.isBlank() || request.destination.isBlank()) return false
        return try {
            NudgeStore.add(context, request)
            refresh(context)
            if (notifyIfPaused && runningService == null) CatNudgeNotifications.postResume(context)
            true
        } catch (error: RuntimeException) {
            Log.e(TAG, "Could not queue invitation", error)
            false
        }
    }

    /** Explicit owner opt-in; invoke only after the setup screen has obtained overlay permission. */
    fun startMonitoring(activity: Activity): Boolean {
        if (!canDrawOverlays(activity)) {
            Toast.makeText(activity, "Allow display over other apps before enabling cat visits.", Toast.LENGTH_LONG).show()
            return false
        }
        val previous = isMonitoringEnabled(activity)
        NudgeStore.setMonitoringEnabled(activity, true)
        if (!startHost(activity)) {
            NudgeStore.setMonitoringEnabled(activity, previous)
            return false
        }
        if (!previous) invokeSafely("monitoring callback") { configuration?.onMonitoringChanged?.invoke(activity.applicationContext, true) }
        return true
    }

    /** Disables monitoring but preserves any already queued invitation until it is handled. */
    fun stopMonitoring(context: Context) {
        val previous = isMonitoringEnabled(context)
        NudgeStore.setMonitoringEnabled(context, false)
        if (previous) invokeSafely("monitoring callback") { configuration?.onMonitoringChanged?.invoke(context.applicationContext, false) }
        refresh(context)
    }

    /**
     * For a visible host Activity, BOOT_COMPLETED, or MY_PACKAGE_REPLACED start exemption only.
     * Ordinary alarm receivers should use enqueue/refresh, not this method.
     */
    fun restoreIfNeeded(context: Context): Boolean {
        if (!isMonitoringEnabled(context) && pendingRequests(context).isEmpty()) return false
        if (runningService != null) { refresh(context); return true }
        if (!canDrawOverlays(context)) { CatNudgeNotifications.postResume(context); return false }
        return startHost(context)
    }

    fun isMonitoringEnabled(context: Context): Boolean = NudgeStore.monitoringEnabled(context)

    fun setAppOpenGateEnabled(context: Context, enabled: Boolean) {
        // Revoked/denied Usage Access always leaves the normal screen-readiness path usable.
        NudgeStore.setAppOpenGateEnabled(context, enabled && hasUsageAccess(context))
        refresh(context)
    }

    fun isAppOpenGateEnabled(context: Context): Boolean = NudgeStore.appOpenGateEnabled(context)

    fun hasUsageAccess(context: Context): Boolean = try {
        val manager = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            manager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: RuntimeException) { false }

    /** Notify an existing host only. This method deliberately never starts a service. */
    fun refresh(context: Context) {
        runningService?.requestRefresh()
        if (pendingRequests(context).isEmpty() && !isMonitoringEnabled(context)) CatNudgeNotifications.cancelResume(context)
    }

    /** Emergency dismissal clears every queued visit; daily monitoring can remain enabled. */
    fun dismiss(context: Context) {
        val removed = NudgeStore.clear(context)
        refresh(context)
        removed.forEach { request -> reportOutcome(context, request, NudgeOutcome.DISMISSED) }
    }

    fun pendingRequests(context: Context): List<NudgeRequest> = NudgeStore.entries(context).map { it.request }

    internal fun startHost(context: Context): Boolean = try {
        val service = Intent(context, CatNudgeService::class.java)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
        CatNudgeNotifications.cancelResume(context)
        true
    } catch (error: RuntimeException) {
        Log.e(TAG, "Could not start cat host from this context", error)
        CatNudgeNotifications.postResume(context)
        Toast.makeText(context, "Open the app to resume cat visits.", Toast.LENGTH_LONG).show()
        false
    }

    internal fun reportOutcome(context: Context, request: NudgeRequest, outcome: NudgeOutcome) {
        invokeSafely("outcome callback") { configuration?.onOutcome?.invoke(context.applicationContext, request, outcome) }
    }

    internal fun invokeSafely(label: String, action: () -> Unit) {
        try { action() } catch (error: RuntimeException) { Log.e(TAG, "Host $label failed", error) }
    }
}
