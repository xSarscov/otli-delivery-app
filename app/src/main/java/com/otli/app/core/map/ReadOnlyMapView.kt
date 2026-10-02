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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.otli.app.R
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

private val ReadOnlyMapHeight = 240.dp
private const val DROPOFF_ZOOM = 15.0
private const val FRAME_PADDING_PX = 120
private const val COURIER_ICON_SIZE_PX = 56
private const val COURIER_ICON_COLOR = 0xFF1565C0.toInt()

/** Slot that draws the [dropoff] pin and, once there is one, the courier; tests substitute their own. */
typealias ReadOnlyMapContent = @Composable (dropoff: MapPin, courier: MapPin?) -> Unit

/** The real MapLibre map. */
val NativeReadOnlyMapContent: ReadOnlyMapContent = { dropoff, courier ->
    ReadOnlyMapView(dropoff, courier, Modifier.fillMaxWidth().height(ReadOnlyMapHeight))
}

/**
 * A map nobody edits: the delivery address pin and, when known, the courier's marker. It can be panned
 * and zoomed but never taps a pin. The camera frames the dropoff, and frames both points once when the
 * courier first appears; later updates only move the marker, so a viewer who panned is not yanked back.
 * Thin MapLibre adapter, verified manually on a device (native rendering cannot run on the JVM).
 */
@Composable
fun ReadOnlyMapView(
    dropoff: MapPin,
    courier: MapPin?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentDropoff by rememberUpdatedState(dropoff)
    val currentCourier by rememberUpdatedState(courier)
    val dropoffTitle = stringResource(R.string.map_dropoff_marker)
    val courierTitle = stringResource(R.string.map_courier_marker)
    val holder = remember { ReadOnlyMapHolder(courierIcon(context), dropoffTitle, courierTitle) }
    val mapView = rememberMapView(
        onMapReady = { view, map ->
            view.loadOtliStyle(map) { holder.attach(map, currentDropoff, currentCourier) }
        },
        onDisposed = { holder.detach() },
    )

    AndroidView(factory = { mapView }, modifier = modifier, update = { holder.render(dropoff, courier) })
}

/** A blue dot with a white rim, to tell the courier apart from the default dropoff marker. */
private fun courierIcon(context: Context): Icon {
    val bitmap = Bitmap.createBitmap(COURIER_ICON_SIZE_PX, COURIER_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = COURIER_ICON_SIZE_PX / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    canvas.drawCircle(center, center, center, paint)
    paint.color = COURIER_ICON_COLOR
    canvas.drawCircle(center, center, center - 6f, paint)
    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

/** Keeps the two markers and the camera in sync with the points; a no-op until the map has a style. */
private class ReadOnlyMapHolder(
    private val courierIcon: Icon,
    private val dropoffTitle: String,
    private val courierTitle: String,
) {
    private var map: MapLibreMap? = null
    private var dropoffMarker: Marker? = null
    private var courierMarker: Marker? = null
    private var framedWithCourier = false

    /** Called when a style is ready; also after a fallback style replaced the first one, so markers start afresh. */
    fun attach(map: MapLibreMap, dropoff: MapPin, courier: MapPin?) {
        this.map = map
        dropoffMarker = null
        courierMarker = null
        framedWithCourier = false
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(dropoff.toLatLng(), DROPOFF_ZOOM))
        render(dropoff, courier)
    }

    fun detach() {
        map = null
    }

    @Suppress("DEPRECATION")
    fun render(dropoff: MapPin, courier: MapPin?) {
        val map = map ?: return
        val dropoffAt = dropoff.toLatLng()
        val shownDropoff = dropoffMarker
        if (shownDropoff == null) {
            dropoffMarker = map.addMarker(MarkerOptions().position(dropoffAt).title(dropoffTitle))
        } else if (shownDropoff.position != dropoffAt) {
            shownDropoff.position = dropoffAt
        }

        val shownCourier = courierMarker
        when {
            courier == null -> {
                shownCourier?.let(map::removeMarker)
                courierMarker = null
            }
            shownCourier == null -> courierMarker = map.addMarker(MarkerOptions().position(courier.toLatLng()).icon(courierIcon).title(courierTitle))
            shownCourier.position != courier.toLatLng() -> shownCourier.position = courier.toLatLng()
        }

        if (courier != null && !framedWithCourier) {
            framedWithCourier = true
            val bounds = LatLngBounds.Builder().include(dropoffAt).include(courier.toLatLng()).build()
            map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, FRAME_PADDING_PX))
        }
    }

    private fun MapPin.toLatLng() = LatLng(latitude, longitude)
}
