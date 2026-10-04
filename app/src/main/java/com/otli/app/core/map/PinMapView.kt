package com.otli.app.core.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.annotations.Marker
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

private const val INITIAL_ZOOM = 13.0

/**
 * Thin MapLibre adapter: draws [state]'s pin as a marker and reports taps. All decisions live in
 * [MapPinPickerState]; this file only translates to MapLibre (the view lifecycle is [rememberMapView],
 * the style [loadOtliStyle]). Verified manually on a device (native rendering cannot run on the JVM).
 */
@Composable
fun PinMapView(
    state: MapPinPickerState,
    onTap: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentState by rememberUpdatedState(state)
    val currentOnTap by rememberUpdatedState(onTap)
    val holder = remember { MapHolder() }
    val mapView = rememberMapView(
        onMapReady = { view, map ->
            holder.map = map
            map.setOnMarkerClickListener(ConsumeMarkerClicks)
            view.loadOtliStyle(map) {
                val start = currentState.center
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(start.latitude, start.longitude), INITIAL_ZOOM))
                holder.render(currentState.pin)
            }
            map.addOnMapClickListener { point ->
                currentOnTap(point.latitude, point.longitude)
                true
            }
        },
        onDisposed = { holder.map = null },
    )

    AndroidView(factory = { mapView }, modifier = modifier, update = { holder.render(state.pin) })
}

/** Keeps the single marker in sync with the pin; a no-op until the map has loaded. */
private class MapHolder {
    var map: MapLibreMap? = null
    private var marker: Marker? = null

    fun render(pin: MapPin?) {
        val map = map ?: return
        marker?.let(map::removeMarker)
        marker = pin?.let { map.addMarker(markerOptions(it)) }
    }
}
