package com.example.junction.sola.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.junction.nudge.NudgeController
import com.junction.catnudge.CatNudge
import kotlinx.coroutines.delay

private tailrec fun Context.activity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }

/** One owner opt-in shared by onboarding and Settings; system grants remain explicit. */
@Composable
fun ReminderControls(available: Boolean) {
    val context = LocalContext.current
    val activity = context.activity()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(CatNudge.isMonitoringEnabled(context)) }
    var overlay by remember { mutableStateOf(CatNudge.canDrawOverlays(context)) }
    var pendingEnable by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notifications by remember { mutableStateOf(true) }
    fun enable() {
        if (available && activity != null) {
            enabled = CatNudge.startMonitoring(activity)
            if (!enabled) error = "Open Junction and try enabling visits again."
        }
    }
    val notificationGrant =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notifications = it
        }
    val overlayGrant =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            overlay = CatNudge.canDrawOverlays(context)
            if (pendingEnable && overlay) enable()
            pendingEnable = false
        }
    LaunchedEffect(lifecycle, available) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                enabled = CatNudge.isMonitoringEnabled(context)
                overlay = CatNudge.canDrawOverlays(context)
                notifications =
                    Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                delay(1000)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleRow(
            "Cat check-in visits",
            if (!available) "Use your own journal to enable daily visits."
            else if (enabled && overlay) "On · following the times above"
            else if (enabled) "Paused · allow display over other apps"
            else "A small visitor when it’s time to check in.",
            enabled && available,
        ) { on ->
            error = null
            if (!available)
                error = "Sample and preview screens do not change your real reminder settings."
            else if (!on) {
                CatNudge.stopMonitoring(context)
                enabled = false
                pendingEnable = false
            } else if (overlay) enable()
            else {
                pendingEnable = true
                try {
                    overlayGrant.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                } catch (_: android.content.ActivityNotFoundException) {
                    pendingEnable = false
                    error = "Overlay settings are unavailable on this phone."
                }
            }
        }
        Body(
            "Visits wait until the phone has been unlocked for 15 seconds. Turning the screen off keeps the invitation and its message for later."
        )
        if (enabled && !notifications && Build.VERSION.SDK_INT >= 33)
            SoftButton(
                "Allow visit notifications",
                { notificationGrant.launch(Manifest.permission.POST_NOTIFICATIONS) },
                Modifier.fillMaxWidth(),
            )
        if (enabled && !overlay)
            SoftButton(
                "Allow display over other apps",
                {
                    try {
                        overlayGrant.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                        )
                    } catch (_: android.content.ActivityNotFoundException) {
                        error = "Overlay settings are unavailable."
                    }
                },
                Modifier.fillMaxWidth(),
            )
        if (available && overlay)
            TextButton({ activity?.let { NudgeController.show(it) } }) {
                Text("Preview the cat", color = Muted)
            }
        error?.let { Body(it) }
    }
}
