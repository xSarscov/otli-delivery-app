package com.otli.app.core.map

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/** OpenFreeMap vector style; free, keyless, and its attribution is shown by [MapPinPicker]. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val INITIAL_ZOOM = 13.0

/**
 * Thin MapLibre adapter: owns the native [MapView] lifecycle, draws [state]'s pin as a marker and
 * reports taps. All decisions live in [MapPinPickerState]; this file only translates to MapLibre.
 * Verified manually on a device (native rendering cannot run on the JVM).
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun PinMapView(
    state: MapPinPickerState,
    onTap: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentState by rememberUpdatedState(state)
    val currentOnTap by rememberUpdatedState(onTap)
    val holder = remember { MapHolder() }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).also { view ->
            view.onCreate(null)
            // Keep the parent (a scrolling form) from stealing pan gestures made on the map.
            view.setOnTouchListener { touched, event ->
                val lock = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
                touched.parent?.requestDisallowInterceptTouchEvent(lock)
                false
            }
            view.getMapAsync { map ->
                holder.map = map
                map.setStyle(STYLE_URL) {
                    val start = currentState.center
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(start.latitude, start.longitude), INITIAL_ZOOM))
                    holder.render(currentState.pin)
                }
                map.addOnMapClickListener { point ->
                    currentOnTap(point.latitude, point.longitude)
                    true
                }
            }
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        // The observer replays events up to the current state only when added, so catch up here.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        onDispose {
            lifecycle.removeObserver(observer)
            holder.map = null
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier, update = { holder.render(state.pin) })
}

/** Keeps the single marker in sync with the pin; a no-op until the map has loaded. */
private class MapHolder {
    var map: MapLibreMap? = null
    private var marker: Marker? = null

    fun render(pin: MapPin?) {
        val map = map ?: return
        marker?.let(map::removeMarker)
        marker = pin?.let { map.addMarker(MarkerOptions().position(LatLng(it.latitude, it.longitude))) }
    }
}
