package com.otli.app.ordering.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Container: wires [CustomerOrdersViewModel] to the customer's order list. */
@Composable
fun CustomerOrdersScreen(
    onOrderClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomerOrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CustomerOrdersContent(state = state, onOrderClick = onOrderClick, onBack = onBack, modifier = modifier)
}
