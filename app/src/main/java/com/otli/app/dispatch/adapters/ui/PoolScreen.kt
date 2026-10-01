package com.otli.app.dispatch.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Container: wires [PoolViewModel] to the pool of the courier home. */
@Composable
fun PoolScreen(
    modifier: Modifier = Modifier,
    viewModel: PoolViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PoolContent(
        state = state,
        onClaim = viewModel::claim,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
    )
}
