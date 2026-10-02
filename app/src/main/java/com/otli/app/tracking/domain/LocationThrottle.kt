package com.otli.app.tracking.domain

/**
 * Client-side throttle of the courier's location writes (ADR-8): publish a fix when at least
 * [MIN_INTERVAL_MILLIS] and [MIN_DISTANCE_METERS] have passed since the last published one, or when
 * [HEARTBEAT_MILLIS] have passed even if the courier has not moved. The first fix always publishes.
 * The rules enforce a 5 s floor on top of this, so a buggy client cannot exhaust the write quota.
 */
object LocationThrottle {
    const val MIN_INTERVAL_MILLIS = 10_000L
    const val MIN_DISTANCE_METERS = 10.0
    const val HEARTBEAT_MILLIS = 60_000L

    /** [last] is the last fix that was actually published, null before the first one. */
    fun shouldPublish(last: GeoFix?, next: GeoFix): Boolean {
        if (last == null) return true
        val elapsed = next.timestampMillis - last.timestampMillis
        if (elapsed >= HEARTBEAT_MILLIS) return true
        return elapsed >= MIN_INTERVAL_MILLIS && last.distanceMetersTo(next) >= MIN_DISTANCE_METERS
    }
}
