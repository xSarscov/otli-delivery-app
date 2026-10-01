package com.otli.app.core.notification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

object NotificationPermission {
    /** Android 13 introduced the runtime permission; earlier versions allow notifications by default. */
    fun shouldRequest(sdkInt: Int, granted: Boolean): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU && !granted
}

/**
 * Asks once for the notification permission when this enters the composition. A refusal is fine:
 * the app keeps working and the notices are simply not shown.
 */
@Composable
fun RequestNotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (NotificationPermission.shouldRequest(Build.VERSION.SDK_INT, granted)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
