package com.example.junction.nudge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.junction.dailyquiz.DailyQuizScheduler
import com.junction.catnudge.CatNudge
import com.junction.catnudge.NudgeMessages
import com.junction.catnudge.NudgeRequest

/** Entry point for other features in this app. Call show from a user interaction. */
object NudgeController {
    const val ACTION_SHOW = "com.example.junction.action.SHOW_NUDGE"
    const val ACTION_OPEN_QUESTIONS = "com.example.junction.action.OPEN_QUESTIONS"
    const val EXTRA_SESSION = "nudge_session"

    fun canShow(context: Context): Boolean = CatNudge.canDrawOverlays(context)

    /** External/background callers still need to satisfy Android activity launch rules. */
    fun show(context: Context): Boolean = try {
        context.startActivity(Intent(context, NudgeTriggerActivity::class.java).apply {
            action = ACTION_SHOW
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (error: RuntimeException) {
        Toast.makeText(context, "Open Junction to send the cat a nudge.", Toast.LENGTH_LONG).show()
        false
    }

    fun dismiss(context: Context) {
        CatNudge.dismiss(context)
    }

    internal fun startFromVisibleActivity(activity: Activity): Boolean {
        if (!canShow(activity)) return false
        return try {
            if (activity.intent.getStringExtra("mode") == "enable-daily") {
                val started = CatNudge.startMonitoring(activity)
                DailyQuizScheduler.reschedule(activity)
                return started
            }
            val destination = activity.intent.getStringExtra("destination") ?: DailyQuizScheduler.DESTINATION
            if (destination !in setOf(DailyQuizScheduler.DESTINATION, "home")) {
                Toast.makeText(activity, "Unknown nudge destination: $destination", Toast.LENGTH_LONG).show()
                return false
            }
            val payload = mutableMapOf("period" to (activity.intent.getStringExtra("period") ?: "preview"))
            activity.intent.getStringExtra("date")?.let { payload["date"] = it }
            CatNudge.show(activity, NudgeRequest(
                id = activity.intent.getStringExtra("nudge_id") ?: "manual-preview",
                destination = destination,
                payload = payload,
                copy = if (activity.intent.getStringExtra("copy") == "original") NudgeMessages.original else DailyQuizScheduler.nextCopy(activity),
                waitForReadyScreen = activity.intent.getBooleanExtra("wait_ready", false),
            ))
        } catch (error: RuntimeException) {
            android.util.Log.e("CatNudge", "Could not start the nudge", error)
            Toast.makeText(activity, "Couldn’t send the cat. Try again from Junction.", Toast.LENGTH_LONG).show()
            false
        }
    }
}
