package com.otli.app.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

private val ReadOnlyMapHeight = 240.dp
private const val SINGLE_POINT_ZOOM = 15.0
private const val FRAME_PADDING_PX = 120
private const val DESTINATION_DOT_SIZE_PX = 64
private const val COURIER_DOT_SIZE_PX = 56
private const val MUTED_DOT_SIZE_PX = 36

/** Slot that draws a [MapScene]; tests substitute their own. */
typealias ReadOnlyMapContent = @Composable (scene: MapScene) -> Unit

/** The real MapLibre map. */
val NativeReadOnlyMapContent: ReadOnlyMapContent = { scene ->
    ReadOnlyMapView(scene, Modifier.fillMaxWidth().height(ReadOnlyMapHeight))
}

/**
 * A map nobody edits: the delivery address pin, the store pin when the scene has one, and the courier's
 * marker when known. It can be panned and zoomed but never taps a pin. The pin the viewer is heading to
 * is a large red dot, the other a small grey dot, the courier a blue one; they are named by the [MapLegend]
 * the screen draws under the map, never by a native info window (see [markerOptions]). The camera fits the destination (and
 * the courier once known) whenever [MapScene.framingKey] changes; later updates only move the courier's
 * marker. No routes. Thin MapLibre adapter, verified manually on a device (native rendering cannot run
 * on the JVM).
 */
@Composable
fun ReadOnlyMapView(
    scene: MapScene,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentScene by rememberUpdatedState(scene)
    val holder = remember { ReadOnlyMapHolder(MarkerIcons.create(context)) }
    val mapView = rememberMapView(
        onMapReady = { view, map ->
            view.loadOtliStyle(map) { holder.attach(map, currentScene) }
        },
        onDisposed = { holder.detach() },
    )

    AndroidView(factory = { mapView }, modifier = modifier, update = { holder.render(scene) })
}

/** The three [MapMarkerLook]s of a marker as dots, so the legend's swatches match the pins. */
private class MarkerIcons(val destination: Icon, val muted: Icon, val courier: Icon) {
    companion object {
        fun create(context: Context): MarkerIcons {
            val factory = IconFactory.getInstance(context)
            return MarkerIcons(
                destination = factory.fromBitmap(dot(DESTINATION_DOT_SIZE_PX, MapMarkerLook.DESTINATION.argb)),
                muted = factory.fromBitmap(dot(MUTED_DOT_SIZE_PX, MapMarkerLook.MUTED.argb)),
                courier = factory.fromBitmap(dot(COURIER_DOT_SIZE_PX, MapMarkerLook.COURIER.argb)),
            )
        }

        /** A coloured dot with a white rim. */
        private fun dot(size: Int, color: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val center = size / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE }
            canvas.drawCircle(center, center, center, paint)
            paint.color = color
            canvas.drawCircle(center, center, center - 6f, paint)
            return bitmap
        }
    }
}

/** Keeps the markers and the camera in sync with the scene; a no-op until the map has a style. */
@Suppress("DEPRECATION")
private class ReadOnlyMapHolder(private val icons: MarkerIcons) {
    private var map: MapLibreMap? = null
    private var dropoffMarker: Marker? = null
    private var pickupMarker: Marker? = null
    private var courierMarker: Marker? = null
    private var framedKey: MapScene.FramingKey? = null
    private var framedOnce = false

    /** Called when a style is ready; also after a fallback style replaced the first one, so markers start afresh. */
    fun attach(map: MapLibreMap, scene: MapScene) {
        this.map = map
        map.setOnMarkerClickListener(ConsumeMarkerClicks)
        dropoffMarker = null
        pickupMarker = null
        courierMarker = null
        framedKey = null
        framedOnce = false
        render(scene)
    }

    fun detach() {
        map = null
    }

    fun render(scene: MapScene) {
        val map = map ?: return
        dropoffMarker = sync(
            map, dropoffMarker, scene.dropoff,
            icon = if (scene.dropoffEmphasized) icons.destination else icons.muted,
        )
        pickupMarker = sync(
            map, pickupMarker, scene.pickup,
            icon = if (scene.pickupEmphasized) icons.destination else icons.muted,
        )
        courierMarker = sync(map, courierMarker, scene.courier, icon = icons.courier)

        if (scene.framingKey != framedKey) {
            framedKey = scene.framingKey
            frame(map, scene.framedPoints)
        }
    }

    /** Adds, moves or removes one marker so it matches [at]; returns the marker now on the map, or null. */
    private fun sync(map: MapLibreMap, current: Marker?, at: MapPin?, icon: Icon): Marker? {
        if (at == null) {
            current?.let(map::removeMarker)
            return null
        }
        if (current == null) return map.addMarker(markerOptions(at, icon))
        val position = LatLng(at.latitude, at.longitude)
        if (current.position != position) current.position = position
        if (current.icon != icon) current.icon = icon
        return current
    }

    private fun frame(map: MapLibreMap, points: List<MapPin>) {
        val latLngs = points.map { LatLng(it.latitude, it.longitude) }
        val update = if (latLngs.size == 1) {
            CameraUpdateFactory.newLatLngZoom(latLngs.first(), SINGLE_POINT_ZOOM)
        } else {
            CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(latLngs).build(), FRAME_PADDING_PX)
        }
        if (framedOnce) map.easeCamera(update) else map.moveCamera(update)
        framedOnce = true
    }
}
