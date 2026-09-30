package com.otli.app.core.map

/** A latitude/longitude pair chosen on the map. */
data class MapPin(val latitude: Double, val longitude: Double)

/**
 * Pure state behind the reusable map pin picker: where the map is centred and which pin, if any,
 * the user has dropped. Rendering lives in the MapLibre adapter; this holder is JVM-testable.
 */
data class MapPinPickerState(
    val center: MapPin = NAGAROTE,
    val pin: MapPin? = null,
) {
    /** The chosen location, or null until a pin is dropped. */
    val result: MapPin? get() = pin

    /** Drops or moves the pin; coordinates outside the valid range leave the state unchanged. */
    fun withPin(latitude: Double, longitude: Double): MapPinPickerState =
        if (isValid(latitude, longitude)) copy(pin = MapPin(latitude, longitude)) else this

    fun cleared(): MapPinPickerState = copy(pin = null)

    companion object {
        /** Default map centre: Nagarote, the launch town. */
        val NAGAROTE = MapPin(latitude = 12.2656, longitude = -86.5664)

        /** Starts on [pin] (and centred on it) when one already exists, e.g. when editing. */
        fun startingAt(pin: MapPin?): MapPinPickerState =
            if (pin == null) MapPinPickerState() else MapPinPickerState(center = pin, pin = pin)

        private fun isValid(latitude: Double, longitude: Double) =
            latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
