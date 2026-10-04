package com.otli.app.dispatch.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otli.app.core.map.NativeReadOnlyMapContent
import com.otli.app.tracking.adapters.ui.LocationPermission
import com.otli.app.tracking.adapters.ui.OwnPositionViewModel

/**
 * Container: wires [ActiveDeliveryViewModel], the native delivery map and the courier's own position
 * (from [OwnPositionViewModel], using the [permission] owned by the courier home) to the delivery of the courier home.
 */
@Composable
fun ActiveDeliveryScreen(
    permission: LocationPermission,
    modifier: Modifier = Modifier,
    viewModel: ActiveDeliveryViewModel = hiltViewModel(),
    ownPositionViewModel: OwnPositionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ownPosition by ownPositionViewModel.position.collectAsStateWithLifecycle()
    val wanted = ownPositionWanted(permission.granted, state)
    LaunchedEffect(wanted) { ownPositionViewModel.setWanted(wanted) }
    ActiveDeliveryContent(
        state = state,
        onPickUp = viewModel::pickUp,
        onDeliver = viewModel::deliver,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
        deliveryMap = NativeReadOnlyMapContent,
        ownPosition = ownPosition,
    )
}

/** The courier's own dot is only worth a location request while a delivery is on screen and the permission is held. */
internal fun ownPositionWanted(permissionGranted: Boolean, state: ActiveDeliveryUiState): Boolean =
    permissionGranted && state.order != null
