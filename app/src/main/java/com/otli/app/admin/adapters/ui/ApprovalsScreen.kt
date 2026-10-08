package com.otli.app.admin.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Container: wires [ApprovalsViewModel] to the merchant and courier account list. */
@Composable
fun ApprovalsScreen(modifier: Modifier = Modifier, viewModel: ApprovalsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ApprovalsContent(
        state = state,
        onApprove = viewModel::approve,
        onSuspend = viewModel::suspend,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}
