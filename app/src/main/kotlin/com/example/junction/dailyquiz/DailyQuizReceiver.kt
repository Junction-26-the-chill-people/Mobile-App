package com.example.junction.dailyquiz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.junction.catnudge.CatNudge

class DailyQuizReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!com.example.junction.FrontendFeatures.CAT_DAILY_INTEGRATION) return
        // An ordinary alarm records work; it never starts a foreground service from background.
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            CatNudge.restoreIfNeeded(context)
        }
        DailyQuizScheduler.reconcile(context)
        DailyQuizScheduler.reschedule(context)
        CatNudge.refresh(context)
    }
}
