package com.junction.catnudge

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/** Notification taps give the host a visible lifecycle before starting its foreground service. */
class CatNudgeResumeActivity : Activity() {
    private var requestedPermission = false
    private var awaitingResult = false
    private var resumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedPermission = savedInstanceState?.getBoolean("requested_permission") ?: false
    }

    override fun onPostResume() {
        super.onPostResume()
        if (resumed || awaitingResult || isFinishing) return
        if (CatNudge.configuration == null) {
            openLauncher()
            finish()
        } else if (CatNudge.canDrawOverlays(this)) {
            resumed = true
            CatNudge.restoreIfNeeded(this)
            finish()
        } else if (requestedPermission) {
            Toast.makeText(this, "Allow display over other apps to resume cat visits.", Toast.LENGTH_LONG).show()
            openLauncher()
            finish()
        } else {
            requestedPermission = true
            awaitingResult = true
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")), REQUEST_OVERLAY)
            } catch (_: ActivityNotFoundException) {
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), REQUEST_OVERLAY)
                } catch (_: ActivityNotFoundException) { openLauncher(); finish() }
            }
        }
    }

    private fun openLauncher() {
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            try { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: ActivityNotFoundException) { /* The queued visit remains available to the host. */ }
        }
    }

    @Deprecated("Overlay settings result on API 24+")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY) awaitingResult = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("requested_permission", requestedPermission)
        super.onSaveInstanceState(outState)
    }

    private companion object { const val REQUEST_OVERLAY = 260 }
}
