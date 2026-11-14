package com.example.junction.nudge

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.junction.catnudge.CatNudge

/** A small visible lifecycle bridge for ADB, links, shortcuts, and other foreground apps. */
class NudgeTriggerActivity : Activity() {
    private var awaitingPermission = false
    private var launched = false
    private var requestingPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        awaitingPermission = savedInstanceState?.getBoolean("awaiting_permission") ?: false
        if (intent.getBooleanExtra("dismiss", false)) {
            NudgeController.dismiss(this)
            finish()
        } else if (intent.getStringExtra("mode") == "disable-daily") {
            CatNudge.stopMonitoring(this)
            finish()
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (isFinishing || launched || requestingPermission) return
        if (NudgeController.canShow(this)) {
            // Run while this Activity is resumed; SAW alone is insufficient on Android 15+.
            launched = true
            NudgeController.startFromVisibleActivity(this)
            finish()
        } else if (awaitingPermission) {
            Toast.makeText(this, "Allow display over other apps to let the cat visit.", Toast.LENGTH_LONG).show()
            finish()
        } else {
            awaitingPermission = true
            requestingPermission = true
            Toast.makeText(this, "Allow Junction to display over other apps, then come back.", Toast.LENGTH_LONG).show()
            try {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                    PERMISSION_REQUEST
                )
            } catch (_: ActivityNotFoundException) {
                try {
                    startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), PERMISSION_REQUEST)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, "Overlay settings aren’t available on this device.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    @Deprecated("Used for the system overlay settings result on API 24+")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSION_REQUEST) requestingPermission = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("awaiting_permission", awaitingPermission)
        super.onSaveInstanceState(outState)
    }

    private companion object { const val PERMISSION_REQUEST = 42 }
}
