package com.otli.app.catalog.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Container: wires [MerchantListViewModel] to the stateless list. */
@Composable
fun MerchantListScreen(
    onMerchantClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MerchantListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MerchantListContent(state = state, onMerchantClick = onMerchantClick, modifier = modifier)
}
