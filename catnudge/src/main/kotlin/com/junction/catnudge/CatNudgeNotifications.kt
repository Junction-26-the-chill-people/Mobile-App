package com.junction.catnudge

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

internal object CatNudgeNotifications {
    const val FOREGROUND_ID = 2601
    private const val RESUME_ID = 2602
    private const val CHANNEL = "catnudge_visits"
    private const val RESUME_CHANNEL = "catnudge_resume"

    fun foreground(context: Context): Notification {
        val monitoring = CatNudge.isMonitoringEnabled(context)
        val pending = CatNudge.pendingRequests(context).isNotEmpty()
        val text = when {
            pending -> "An invitation is waiting for you. Screen-off pauses the visit."
            monitoring -> "Cat visits are enabled. The cat waits until your screen is ready."
            else -> "Cat visits are ready."
        }
        val builder = builder(context, CHANNEL, "Cat visits", NotificationManager.IMPORTANCE_LOW)
            .setSmallIcon(R.drawable.catnudge_notification)
            .setContentTitle(if (monitoring) "Cat visits enabled" else "A little visitor")
            .setContentText(text).setContentIntent(resumeIntent(context))
            .setOngoing(true).setShowWhen(false).setCategory(Notification.CATEGORY_SERVICE)
        if (pending) {
            builder.addAction(Notification.Action.Builder(null, "Dismiss visit",
                serviceIntent(context, CatNudgeService.ACTION_DISMISS, 2)).build())
        }
        if (monitoring) {
            builder.addAction(Notification.Action.Builder(null, "Disable visits",
                serviceIntent(context, CatNudgeService.ACTION_DISABLE_MONITOR, 3)).build())
        }
        return builder.build()
    }

    fun postResume(context: Context) {
        if (CatNudge.pendingRequests(context).isEmpty() && !CatNudge.isMonitoringEnabled(context)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        try {
            val notification = builder(context, RESUME_CHANNEL, "Resume cat visits", NotificationManager.IMPORTANCE_DEFAULT)
                .setSmallIcon(R.drawable.catnudge_notification)
                .setContentTitle(if (CatNudge.pendingRequests(context).isEmpty()) "Cat visits paused" else "A cat invitation is waiting")
                .setContentText("Tap to resume visits or review display-over-apps permission.")
                .setContentIntent(resumeIntent(context)).setAutoCancel(true).build()
            context.getSystemService(NotificationManager::class.java).notify(RESUME_ID, notification)
        } catch (error: RuntimeException) {
            Log.w(CatNudge.TAG, "Could not show resume notification", error)
        }
    }

    fun cancelResume(context: Context) { context.getSystemService(NotificationManager::class.java).cancel(RESUME_ID) }

    private fun resumeIntent(context: Context) = PendingIntent.getActivity(context, 1,
        Intent(context, CatNudgeResumeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun serviceIntent(context: Context, action: String, code: Int) = PendingIntent.getService(context, code,
        Intent(context, CatNudgeService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Suppress("DEPRECATION")
    private fun builder(context: Context, channel: String, title: String, importance: Int): Notification.Builder {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(channel, title, importance))
            return Notification.Builder(context, channel)
        }
        return Notification.Builder(context).setPriority(Notification.PRIORITY_LOW)
    }
}
