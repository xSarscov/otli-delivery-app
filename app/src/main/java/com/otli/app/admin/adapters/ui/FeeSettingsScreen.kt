package com.otli.app.admin.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Container: wires [FeeSettingsViewModel] to the fee editor. */
@Composable
fun FeeSettingsScreen(modifier: Modifier = Modifier, viewModel: FeeSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FeeSettingsContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onSave = viewModel::save,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}
