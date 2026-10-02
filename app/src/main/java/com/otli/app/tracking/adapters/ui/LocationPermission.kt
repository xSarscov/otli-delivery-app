package com.otli.app.tracking.adapters.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Whether the app may read the precise location, and a way to ask the user for it. */
@Stable
class LocationPermission internal constructor(initiallyGranted: Boolean) {
    var granted: Boolean by mutableStateOf(initiallyGranted)
        internal set

    internal var launch: () -> Unit = {}

    /** Shows the system dialog unless the permission is already held. A denial is not an error. */
    fun request() {
        if (!granted) launch()
    }
}

private fun isGranted(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * The location permission as Compose state. It is re-read whenever the screen resumes, so granting it
 * in the system settings while the app is in the background is noticed too.
 */
@Composable
fun rememberLocationPermission(): LocationPermission {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = remember { LocationPermission(isGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission.granted = isGranted(context)
    }
    SideEffect { permission.launch = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission.granted = isGranted(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return permission
}
