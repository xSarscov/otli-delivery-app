package com.otli.app.tracking.adapters.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.otli.app.tracking.application.LocationSettingsChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The system location switch as a [Flow]: [LocationManagerCompat.isLocationEnabled] now, and again on
 * every broadcast that says the switch or the providers changed. It needs no permission and no Play
 * Services; asking the user to turn it on is a separate step in the UI.
 */
class AndroidLocationSettingsChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationSettingsChecker {

    override fun servicesEnabled(): Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(LocationManager::class.java)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(LocationManagerCompat.isLocationEnabled(manager))
            }
        }
        val filter = IntentFilter().apply {
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(LocationManagerCompat.isLocationEnabled(manager))
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
