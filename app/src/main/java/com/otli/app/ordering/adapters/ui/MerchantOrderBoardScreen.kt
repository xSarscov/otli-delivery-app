package com.otli.app.ordering.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Container: wires [MerchantOrderBoardViewModel] to the order board tab. */
@Composable
fun MerchantOrderBoardScreen(
    modifier: Modifier = Modifier,
    viewModel: MerchantOrderBoardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MerchantOrderBoardContent(
        state = state,
        onAdvance = viewModel::advance,
        onStartReject = viewModel::startReject,
        onRejectReasonChange = viewModel::setRejectReason,
        onConfirmReject = viewModel::confirmReject,
        onDismissReject = viewModel::dismissReject,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}
