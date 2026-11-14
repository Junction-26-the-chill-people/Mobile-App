package com.example.junction

import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.app.TimePickerDialog
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.junction.nudge.NudgeController
import com.example.junction.ui.nudge.NudgeHomeScreen
import com.example.junction.ui.nudge.ReservedScreen
import com.example.junction.ui.nudge.DailyQuizControls
import com.example.junction.dailyquiz.DailyQuizScheduler
import com.example.junction.dailyquiz.DailyQuizTimes
import com.example.junction.dailyquiz.QuizPeriod
import com.example.junction.dailyquiz.QuizTime
import com.junction.catnudge.CatNudge

class MainActivity : ComponentActivity() {
  private var questionsOpen by mutableStateOf(false)
  private var overlayAllowed by mutableStateOf(false)
  private var notificationsAllowed by mutableStateOf(true)
  private var dailyEnabled by mutableStateOf(false)
  private var quizTimes by mutableStateOf(DailyQuizTimes())
  private var appOpenGate by mutableStateOf(false)
  private var usageAccess by mutableStateOf(false)
  private var quizPeriod by mutableStateOf("preview")
  private var pendingDailyEnable = false
  private var pendingAppGateEnable = false
  private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
    notificationsAllowed = it
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    questionsOpen = savedInstanceState?.getBoolean("questions_open")
      ?: (intent.action == NudgeController.ACTION_OPEN_QUESTIONS)
    quizPeriod = savedInstanceState?.getString("quiz_period") ?: intent.getStringExtra("period") ?: "preview"
    pendingDailyEnable = savedInstanceState?.getBoolean("pending_daily_enable") ?: false
    pendingAppGateEnable = savedInstanceState?.getBoolean("pending_app_gate_enable") ?: false
    enableEdgeToEdge()
    setContent {
      BackHandler(enabled = questionsOpen) { questionsOpen = false }
      if (questionsOpen) {
        ReservedScreen(onBack = { questionsOpen = false }, period = quizPeriod)
      } else {
        NudgeHomeScreen(
          overlayAllowed = overlayAllowed,
          onSend = { NudgeController.show(this) },
          onStop = { NudgeController.dismiss(this) },
          onPreviewQuestions = { questionsOpen = true },
          notificationsAllowed = notificationsAllowed,
          onEnableNotifications = {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
          },
          dailySettings = {
            DailyQuizControls(dailyEnabled, quizTimes, appOpenGate, usageAccess,
              onEnabledChange = { changeDailyMonitoring(it) },
              onTimeChange = { editQuizTime(it) },
              onAppOpenGateChange = { setAppGate(it) })
          },
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    overlayAllowed = NudgeController.canShow(this)
    notificationsAllowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
      this, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    refreshDailySettings()
    if (pendingAppGateEnable) {
      pendingAppGateEnable = false
      if (usageAccess) CatNudge.setAppOpenGateEnabled(this, true)
      refreshDailySettings()
    }
  }

  override fun onPostResume() {
    super.onPostResume()
    if (pendingDailyEnable) {
      pendingDailyEnable = false
      if (NudgeController.canShow(this)) CatNudge.startMonitoring(this)
    } else if (CatNudge.isMonitoringEnabled(this) || CatNudge.pendingRequests(this).isNotEmpty()) {
      CatNudge.restoreIfNeeded(this)
    }
    DailyQuizScheduler.reschedule(this)
    refreshDailySettings()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.action == NudgeController.ACTION_OPEN_QUESTIONS) {
      questionsOpen = true
      quizPeriod = intent.getStringExtra("period") ?: "preview"
    } else if (intent.action == Intent.ACTION_MAIN) questionsOpen = false
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean("questions_open", questionsOpen)
    outState.putString("quiz_period", quizPeriod)
    outState.putBoolean("pending_daily_enable", pendingDailyEnable)
    outState.putBoolean("pending_app_gate_enable", pendingAppGateEnable)
    super.onSaveInstanceState(outState)
  }

  private fun refreshDailySettings() {
    dailyEnabled = CatNudge.isMonitoringEnabled(this)
    quizTimes = DailyQuizScheduler.times(this)
    usageAccess = CatNudge.hasUsageAccess(this)
    appOpenGate = CatNudge.isAppOpenGateEnabled(this)
  }

  private fun changeDailyMonitoring(enabled: Boolean) {
    if (!enabled) {
      CatNudge.stopMonitoring(this)
    } else if (NudgeController.canShow(this)) {
      CatNudge.startMonitoring(this)
    } else {
      pendingDailyEnable = true
      startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }
    refreshDailySettings()
  }

  private fun setAppGate(enabled: Boolean) {
    if (enabled && !CatNudge.hasUsageAccess(this)) {
      pendingAppGateEnable = true
      startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    } else CatNudge.setAppOpenGateEnabled(this, enabled)
    refreshDailySettings()
  }

  private fun editQuizTime(period: QuizPeriod) {
    val current = quizTimes.forPeriod(period)
    TimePickerDialog(this, { _, hour, minute ->
      val changed = QuizTime(hour, minute)
      val times = if (period == QuizPeriod.MORNING) quizTimes.copy(morning = changed) else quizTimes.copy(evening = changed)
      DailyQuizScheduler.updateTimes(this, times)
      refreshDailySettings()
    }, current.hour, current.minute, true).show()
  }
}
