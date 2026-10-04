package com.otli.app.core.map

import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * MapLibre draws a marker's info window natively, above the Compose tree and outside the map's bounds, so
 * on a scrolling screen it floats over the rest of the content. None of our maps opens one: markers are
 * made here, with neither title nor snippet, and [ConsumeMarkerClicks] swallows the tap that would select
 * them. Pins are named by a [MapLegend] in the layout instead.
 */
@Suppress("DEPRECATION")
fun markerOptions(pin: MapPin, icon: Icon? = null): MarkerOptions =
    MarkerOptions().position(LatLng(pin.latitude, pin.longitude)).apply { if (icon != null) icon(icon) }

/** Install with [MapLibreMap.setOnMarkerClickListener]; returning true tells the map the tap is handled, so nothing opens. */
@Suppress("DEPRECATION")
val ConsumeMarkerClicks = MapLibreMap.OnMarkerClickListener { true }
