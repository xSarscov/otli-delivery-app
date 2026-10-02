package com.otli.app.core.map

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * The native [MapView] every Otli map shares: created once, tied to the lifecycle of the screen, and
 * keeping a scrolling parent from stealing the pan gestures made on the map. [onMapReady] runs when
 * the map is available; [onDisposed] runs before the view is destroyed. Native glue, verified manually
 * on a device (rendering cannot run on the JVM).
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun rememberMapView(
    onMapReady: (view: MapView, map: MapLibreMap) -> Unit,
    onDisposed: () -> Unit = {},
): MapView {
    val context = LocalContext.current
    val currentOnMapReady by rememberUpdatedState(onMapReady)
    val currentOnDisposed by rememberUpdatedState(onDisposed)
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).also { view ->
            view.onCreate(null)
            view.setOnTouchListener { touched, event ->
                val lock = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
                touched.parent?.requestDisallowInterceptTouchEvent(lock)
                false
            }
            view.getMapAsync { map -> currentOnMapReady(view, map) }
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
            currentOnDisposed()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}
