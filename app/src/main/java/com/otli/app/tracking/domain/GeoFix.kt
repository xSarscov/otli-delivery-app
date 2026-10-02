package com.otli.app.tracking.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One position report: where the device is, how sure it is (metres), and when it was taken (epoch millis). */
data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMillis: Long,
) {
    /** Great-circle distance (haversine) to [other] in metres; precise enough for a 10 m threshold. */
    fun distanceMetersTo(other: GeoFix): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
