package com.otli.app.ordering.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otli.app.tracking.adapters.ui.LiveMapScreen

/** Container: wires [OrderTrackingViewModel] (which reads the order id from the route) and the live map to the tracking screen. */
@Composable
fun OrderTrackingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrderTrackingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OrderTrackingContent(
        state = state,
        onCancel = viewModel::cancel,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
        modifier = modifier,
        liveMap = { LiveMapScreen() },
    )
}
