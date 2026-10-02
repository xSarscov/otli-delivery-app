package com.otli.app.core.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.otli.app.R

private val MapHeight = 240.dp

/** Slot that renders the map for a [MapPinPickerState] and reports taps as latitude/longitude. */
typealias PinMapContent = @Composable (state: MapPinPickerState, onTap: (Double, Double) -> Unit) -> Unit

/** The real MapLibre map; tests substitute their own [PinMapContent]. */
val NativePinMapContent: PinMapContent = { state, onTap ->
    PinMapView(state, onTap, Modifier.fillMaxWidth().height(MapHeight))
}

/**
 * Reusable presentational pin picker: a map the user taps to drop a pin, the OpenFreeMap /
 * OpenStreetMap attribution the tile licence requires, and a way to remove the pin. The caller
 * owns [pin]; the map centres on it once (or on Nagarote when there is none). The map itself is
 * the [mapContent] slot so JVM tests can swap the native MapLibre view for a fake.
 */
@Composable
fun MapPinPicker(
    pin: MapPin?,
    onPinChange: (MapPin?) -> Unit,
    modifier: Modifier = Modifier,
    mapContent: PinMapContent = NativePinMapContent,
) {
    // The camera is positioned from the first pin only, so dropping a pin never moves the map.
    val initialCenter = remember { MapPinPickerState.startingAt(pin).center }
    val state = MapPinPickerState(center = initialCenter, pin = pin)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        mapContent(state) { latitude, longitude ->
            state.withPin(latitude, longitude).result?.takeIf { it != pin }?.let(onPinChange)
        }
        MapAttribution()
        if (pin == null) {
            Text(stringResource(R.string.map_pin_hint), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                stringResource(R.string.map_pin_selected, pin.latitude, pin.longitude),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { onPinChange(null) }) { Text(stringResource(R.string.map_pin_clear)) }
        }
    }
}
