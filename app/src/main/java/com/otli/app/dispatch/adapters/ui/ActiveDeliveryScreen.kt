package com.otli.app.dispatch.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otli.app.core.map.NativeReadOnlyMapContent

/** Container: wires [ActiveDeliveryViewModel] and the native delivery map to the delivery of the courier home. */
@Composable
fun ActiveDeliveryScreen(
    modifier: Modifier = Modifier,
    viewModel: ActiveDeliveryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ActiveDeliveryContent(
        state = state,
        onPickUp = viewModel::pickUp,
        onDeliver = viewModel::deliver,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
        deliveryMap = NativeReadOnlyMapContent,
    )
}
