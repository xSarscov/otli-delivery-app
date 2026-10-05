package com.otli.app.admin.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Container: wires [OrderListViewModel] to the Admin order list. */
@Composable
fun OrderListScreen(onOrderClick: (String) -> Unit, modifier: Modifier = Modifier, viewModel: OrderListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OrderListContent(state = state, onOrderClick = onOrderClick, modifier = modifier)
}
