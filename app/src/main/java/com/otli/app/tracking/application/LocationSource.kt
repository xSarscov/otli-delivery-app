package com.otli.app.tracking.application

import com.otli.app.tracking.domain.GeoFix
import kotlinx.coroutines.flow.Flow

/**
 * Port over the device's position. Collecting starts location updates and cancelling the collector stops
 * them. The flow fails (for instance with a [SecurityException] when the location permission is missing)
 * rather than going silent, so collectors must `catch`.
 */
interface LocationSource {
    fun fixes(): Flow<GeoFix>
}
