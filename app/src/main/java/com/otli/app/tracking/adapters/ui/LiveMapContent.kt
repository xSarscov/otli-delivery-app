package com.otli.app.tracking.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.otli.app.R
import com.otli.app.core.map.MapAttribution
import com.otli.app.core.map.MapPin
import com.otli.app.core.map.MapScene
import com.otli.app.core.map.NativeReadOnlyMapContent
import com.otli.app.core.map.ReadOnlyMapContent
import com.otli.app.core.theme.OtliTheme
import com.otli.app.tracking.domain.LivePosition
import com.otli.app.tracking.domain.UpdateAge
import kotlinx.coroutines.delay

private const val CLOCK_TICK_MILLIS = 1_000L

/**
 * Stateless live map of an order: the delivery address, the courier's marker, the tile attribution and
 * how long ago the courier's position was published ([nowMillis] is the phone's clock). It shows
 * nothing unless [state] says the viewer may follow the courier, so the tracking screen can always host it.
 * The map itself is the [mapContent] slot so JVM tests can swap the native MapLibre view for a fake.
 */
@Composable
fun LiveMapContent(
    state: LiveMapUiState,
    nowMillis: Long,
    mapContent: ReadOnlyMapContent,
    modifier: Modifier = Modifier,
) {
    val dropoff = state.dropoff
    if (!state.visible || dropoff == null) return
    val courier = state.courier
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.live_map_title), style = MaterialTheme.typography.titleMedium)
        mapContent(MapScene(dropoff, courier = courier?.let { MapPin(it.latitude, it.longitude) }))
        MapAttribution()
        Text(statusText(state, nowMillis), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun statusText(state: LiveMapUiState, nowMillis: Long): String {
    val courier = state.courier
    return when {
        courier != null -> when (val age = UpdateAge.of(nowMillis, courier.updatedAtMillis)) {
            is UpdateAge.Seconds -> stringResource(R.string.live_map_updated_seconds, age.value)
            is UpdateAge.Minutes -> stringResource(R.string.live_map_updated_minutes, age.value)
        }
        state.locationUnavailable -> stringResource(R.string.live_map_unavailable)
        else -> stringResource(R.string.live_map_waiting)
    }
}

/** Container: wires [LiveMapViewModel] and the native map, and ticks the phone's clock once a second while on screen. */
@Composable
fun LiveMapScreen(
    modifier: Modifier = Modifier,
    viewModel: LiveMapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(CLOCK_TICK_MILLIS)
            }
        }
    }
    LiveMapContent(state = state, nowMillis = now, mapContent = NativeReadOnlyMapContent, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun LiveMapPreview() {
    OtliTheme {
        LiveMapContent(
            state = LiveMapUiState(
                isLoading = false,
                visible = true,
                dropoff = MapPin(12.27, -86.57),
                courier = LivePosition("courier-1", 12.2656, -86.5664, accuracyMeters = 6f, updatedAtMillis = 1_000L),
            ),
            nowMillis = 8_000L,
            mapContent = { Text("map") },
        )
    }
}
