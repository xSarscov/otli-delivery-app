package com.otli.app.dispatch.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Container: wires [AvailabilityViewModel] to the online/offline switch of the courier home. */
@Composable
fun AvailabilityScreen(
    modifier: Modifier = Modifier,
    viewModel: AvailabilityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AvailabilityContent(
        state = state,
        onSetOnline = viewModel::setOnline,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}
