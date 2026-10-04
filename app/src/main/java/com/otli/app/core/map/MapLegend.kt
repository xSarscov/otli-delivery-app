package com.otli.app.core.map

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.otli.app.R

/** How a pin looks on the map: the destination stands out, the other pin is a small grey dot, the courier is blue. */
enum class MapMarkerLook(val argb: Int) {
    DESTINATION(0xFFD32F2F.toInt()),
    MUTED(0xFF757575.toInt()),
    COURIER(0xFF1565C0.toInt()),
}

/** What a legend line calls a pin. */
enum class MapLegendRole(@StringRes val label: Int) {
    PICKUP_HERE(R.string.map_pickup_here_marker),
    STORE(R.string.map_store_marker),
    DELIVER_TO(R.string.map_deliver_to_marker),
    DELIVERY_ADDRESS(R.string.map_dropoff_marker),
    COURIER(R.string.map_courier_marker),
    YOU(R.string.map_you_marker),
}

/** One legend line: a pin of [look] called [role], with the place's own [detail] (its reference) when there is one. */
data class MapLegendEntry(val role: MapLegendRole, val look: MapMarkerLook, val emphasized: Boolean, val detail: String?)

/**
 * The labels of the pins of this scene, in the order store, address, courier. Maps never open a native
 * info window (it floats over the rest of the screen), so these lines, drawn in Compose under the map, are
 * the only place pins are named. [viewerIsCourier] names the courier's own dot "You" and the stressed
 * address "Deliver to"; the customer's map says "Courier" and "Delivery address".
 */
fun MapScene.legend(
    viewerIsCourier: Boolean = false,
    pickupDetail: String? = null,
    dropoffDetail: String? = null,
): List<MapLegendEntry> = listOfNotNull(
    pickup?.let {
        MapLegendEntry(
            role = if (pickupEmphasized) MapLegendRole.PICKUP_HERE else MapLegendRole.STORE,
            look = if (pickupEmphasized) MapMarkerLook.DESTINATION else MapMarkerLook.MUTED,
            emphasized = pickupEmphasized,
            detail = pickupDetail,
        )
    },
    MapLegendEntry(
        role = if (viewerIsCourier && dropoffEmphasized) MapLegendRole.DELIVER_TO else MapLegendRole.DELIVERY_ADDRESS,
        look = if (dropoffEmphasized) MapMarkerLook.DESTINATION else MapMarkerLook.MUTED,
        emphasized = dropoffEmphasized,
        detail = dropoffDetail,
    ),
    courier?.let {
        MapLegendEntry(
            role = if (viewerIsCourier) MapLegendRole.YOU else MapLegendRole.COURIER,
            look = MapMarkerLook.COURIER,
            emphasized = false,
            detail = null,
        )
    },
)

/** The legend of [scene] under its map; the stressed destination is bold with a larger swatch. */
@Composable
fun MapLegend(
    scene: MapScene,
    modifier: Modifier = Modifier,
    viewerIsCourier: Boolean = false,
    pickupDetail: String? = null,
    dropoffDetail: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        scene.legend(viewerIsCourier, pickupDetail, dropoffDetail).forEach { entry ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(if (entry.emphasized) 14.dp else 10.dp).background(Color(entry.look.argb), CircleShape))
                val label = stringResource(entry.role.label)
                Text(
                    entry.detail?.let { stringResource(R.string.map_legend_entry, label, it) } ?: label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (entry.emphasized) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
