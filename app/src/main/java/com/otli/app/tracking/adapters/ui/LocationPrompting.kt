package com.otli.app.tracking.adapters.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.otli.app.tracking.domain.LocationAction
import kotlinx.coroutines.flow.Flow

private const val SETTINGS_CHECK_INTERVAL_MILLIS = 5_000L

/**
 * Answers the one-shot [events] of the tracking view model with the matching system dialog. The latest
 * lambdas are used for every event, so recomposition never drops or repeats one.
 */
@Composable
fun LocationActionsEffect(
    events: Flow<LocationAction>,
    onRequestPermission: () -> Unit,
    onTurnOnServices: () -> Unit,
) {
    val currentRequestPermission by rememberUpdatedState(onRequestPermission)
    val currentTurnOnServices by rememberUpdatedState(onTurnOnServices)
    LaunchedEffect(events) {
        events.collect { action ->
            when (action) {
                LocationAction.REQUEST_PERMISSION -> currentRequestPermission()
                LocationAction.TURN_ON_SERVICES -> currentTurnOnServices()
            }
        }
    }
}

/**
 * The "Turn on location?" dialog of Google Play Services. Android apps cannot flip the device location
 * switch silently: this asks the Settings API whether the switch satisfies a high accuracy request and,
 * when it does not, launches the resolution Play Services provides. Declining or accepting needs no
 * handling here: the switch is observed through [com.otli.app.tracking.application.LocationSettingsChecker],
 * so the status line updates by itself. When Play Services offers no dialog the system location settings
 * open instead. Thin glue, verified manually on a device.
 */
@Composable
fun rememberTurnOnLocationPrompt(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    return remember(context, launcher) {
        {
            val request = LocationSettingsRequest.Builder()
                .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, SETTINGS_CHECK_INTERVAL_MILLIS).build())
                .setAlwaysShow(true)
                .build()
            LocationServices.getSettingsClient(context).checkLocationSettings(request).addOnFailureListener { failure ->
                val resolution = failure.settingsResolution()
                if (resolution != null) {
                    launcher.launch(resolution)
                } else {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }
}

/** The dialog Play Services offers for a failed settings check, or null when the failure cannot be resolved by the user. */
internal fun Exception.settingsResolution(): IntentSenderRequest? =
    (this as? ResolvableApiException)?.let { IntentSenderRequest.Builder(it.resolution).build() }
