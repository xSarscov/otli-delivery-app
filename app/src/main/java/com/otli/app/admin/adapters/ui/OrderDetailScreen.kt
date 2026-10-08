package com.otli.app.admin.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otli.app.ordering.adapters.ui.OrderTrackingViewModel
import com.otli.app.tracking.adapters.ui.LiveMapScreen

/**
 * Container: one order's live state (the tracking view model reads the order id from the route and
 * Admin may read every order) and the live map, which shows itself only while a courier is on the way.
 */
@Composable
fun OrderDetailScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: OrderTrackingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OrderDetailContent(state = state, onBack = onBack, modifier = modifier, liveMap = { LiveMapScreen() })
}
