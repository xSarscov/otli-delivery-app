package com.otli.app.admin.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otli.app.core.time.SystemClock
import kotlinx.coroutines.delay

private const val CLOCK_TICK_MILLIS = 30_000L

/** Container: wires [StuckOrdersViewModel] to the list and keeps the waiting times fresh while it is on screen. */
@Composable
fun StuckOrdersScreen(modifier: Modifier = Modifier, viewModel: StuckOrdersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by produceState(SystemClock().nowMillis()) {
        while (true) {
            delay(CLOCK_TICK_MILLIS)
            value = SystemClock().nowMillis()
        }
    }
    StuckOrdersContent(
        state = state,
        nowMillis = now,
        onRelease = viewModel::release,
        onCancel = viewModel::startCancel,
        onConfirmCancel = viewModel::confirmCancel,
        onDismissCancel = viewModel::dismissCancel,
        onDismissError = viewModel::dismissError,
        modifier = modifier,
    )
}
