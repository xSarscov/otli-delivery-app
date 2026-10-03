package com.otli.app.tracking.application

import kotlinx.coroutines.flow.Flow

/**
 * Port over the device's location switch. Holding the location permission is not enough to get a
 * position: with the location services off the device reports nothing, and nobody is told. This says
 * whether they are on, so the app can ask the courier to turn them on. Android apps cannot flip the
 * switch themselves; asking is the UI's job (the Play Services "Turn on location?" dialog).
 */
interface LocationSettingsChecker {
    /** The current state first, then every change for as long as the flow is collected. */
    fun servicesEnabled(): Flow<Boolean>
}
