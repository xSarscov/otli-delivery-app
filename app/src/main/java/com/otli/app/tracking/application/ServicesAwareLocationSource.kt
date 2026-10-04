package com.otli.app.tracking.application

import com.otli.app.tracking.domain.GeoFix
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * The device position, asked for only while the location services are on and asked for again every time
 * they come back. A request the device received while they were off never delivers, even after the courier
 * turns them on from the warning: the tracking service (which stays up through [com.otli.app.tracking.domain.TrackingPlan.ServicesOff])
 * and the courier's own dot subscribed once and stayed silent until a new sign-in rebuilt them. Gating the
 * subscription here fixes both consumers at once, and stops listening to the device while it is off. A switch
 * that cannot be read counts as on, like the courier home; a failing [device] still fails the collector.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServicesAwareLocationSource(
    private val device: LocationSource,
    private val settings: LocationSettingsChecker,
) : LocationSource {
    override fun fixes(): Flow<GeoFix> = settings.servicesEnabled()
        .catch { emit(true) }
        .distinctUntilChanged()
        .flatMapLatest { on -> if (on) device.fixes() else emptyFlow() }
}
