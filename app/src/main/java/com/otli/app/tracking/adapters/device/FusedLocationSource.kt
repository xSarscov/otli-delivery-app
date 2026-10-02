package com.otli.app.tracking.adapters.device

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.otli.app.tracking.application.LocationSource
import com.otli.app.tracking.domain.GeoFix
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The fused location provider of Google Play Services as a [Flow] of [GeoFix]. It asks for a high
 * accuracy fix every few seconds; deciding which of them are worth publishing is the job of
 * [com.otli.app.tracking.domain.LocationThrottle]. Needs Play Services on the device (the phone and
 * Google Play emulator images have it). The caller must hold `ACCESS_FINE_LOCATION`; without it the
 * flow fails with a [SecurityException].
 */
class FusedLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationSource {

    @SuppressLint("MissingPermission")
    override fun fixes(): Flow<GeoFix> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MILLIS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toGeoFix()) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper()).addOnFailureListener { close(it) }
        } catch (denied: SecurityException) {
            close(denied)
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 5_000L
        const val MIN_UPDATE_INTERVAL_MILLIS = 2_000L
    }
}

/** A location without a reported accuracy counts as exact (zero metres), which the rules accept. */
internal fun Location.toGeoFix(): GeoFix = GeoFix(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = if (hasAccuracy()) accuracy else 0f,
    timestampMillis = time,
)
