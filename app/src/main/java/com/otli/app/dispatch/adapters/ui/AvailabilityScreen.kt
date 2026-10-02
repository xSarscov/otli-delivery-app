package com.otli.app.dispatch.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Container: wires [AvailabilityViewModel] to the online/offline switch of the courier home; [onWentOnline] fires when the courier asks to go online. */
@Composable
fun AvailabilityScreen(
    onWentOnline: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AvailabilityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AvailabilityContent(
        state = state,
        onSetOnline = { online ->
            viewModel.setOnline(online)
            if (online) onWentOnline()
        },
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}
