package com.otli.app.ordering.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Container: wires [CheckoutViewModel] to the checkout form and reports the placed order's id once. */
@Composable
fun CheckoutScreen(
    onPlaced: (orderId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state.placedOrderId?.let { orderId -> LaunchedEffect(orderId) { onPlaced(orderId) } }
    CheckoutContent(
        state = state,
        onPinChange = viewModel::setPin,
        onReferenceChange = viewModel::setReference,
        onCashConfirmedChange = viewModel::setCashConfirmed,
        onPlace = viewModel::place,
        onBack = onBack,
        modifier = modifier,
    )
}
