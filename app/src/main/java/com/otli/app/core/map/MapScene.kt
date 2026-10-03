package com.otli.app.core.map

/** Which end of a delivery the viewer is heading to; that pin is the one the map stresses. */
enum class MapEmphasis { PICKUP, DROPOFF }

/**
 * What a read-only map shows: the delivery address, optionally the store ([pickup]) and the courier, and
 * which of the two destinations is the current one. The customer's live map has no pickup and always
 * stresses the dropoff; the courier's delivery map shows the store first and the dropoff after the
 * order is picked up. Pure data plus the decisions the native map only has to draw (no routes, only
 * pins and a camera fit).
 */
data class MapScene(
    val dropoff: MapPin,
    val courier: MapPin? = null,
    val pickup: MapPin? = null,
    val emphasis: MapEmphasis = MapEmphasis.DROPOFF,
) {
    /** The store is the destination only when there is a store pin to stress. */
    val pickupEmphasized: Boolean get() = pickup != null && emphasis == MapEmphasis.PICKUP

    val dropoffEmphasized: Boolean get() = !pickupEmphasized

    /** The pin the viewer is heading to. */
    val destination: MapPin get() = if (pickupEmphasized && pickup != null) pickup else dropoff

    /** The points the camera fits: the destination and, once known, the courier. */
    val framedPoints: List<MapPin> get() = listOfNotNull(destination, courier)

    /**
     * The camera is refitted when this changes (the destination switched, or the courier appeared or was lost)
     * and left alone while the courier only moves, so a viewer who panned is not yanked back.
     */
    val framingKey: FramingKey get() = FramingKey(destination, courier != null)

    data class FramingKey(val destination: MapPin, val hasCourier: Boolean)
}
