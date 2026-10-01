package com.otli.app.ordering.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.catalog.domain.PriceInput
import com.otli.app.core.theme.OtliTheme
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.ordering.domain.OrderTransitions

object OrderBoardTags {
    const val LOADING = "order-board-loading"
}

/**
 * The text input of the rejection dialog. It is a slot because a text field inside a dialog window
 * never lets Robolectric go idle (the JVM test runs out of memory); the real field is checked on a device.
 */
typealias ReasonField = @Composable (value: String, onValueChange: (String) -> Unit, isError: Boolean) -> Unit

val NativeReasonField: ReasonField = { value, onValueChange, isError ->
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.board_reject_reason_label)) },
        isError = isError,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Stateless merchant order board: new orders to answer, then the orders already under way. */
@Composable
fun MerchantOrderBoardContent(
    state: MerchantOrderBoardUiState,
    onAdvance: (String) -> Unit,
    onStartReject: (String) -> Unit,
    onRejectReasonChange: (String) -> Unit,
    onConfirmReject: () -> Unit,
    onDismissReject: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    placedAt: PlacedAtFormatter = rememberPlacedAtFormatter(),
    reasonField: ReasonField = NativeReasonField,
) {
    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag(OrderBoardTags.LOADING))
            else -> Board(state, placedAt, onAdvance, onStartReject, onDismissError)
        }
    }
    if (state.rejecting != null) {
        RejectDialog(state, reasonField, onRejectReasonChange, onConfirmReject, onDismissReject)
    }
}

@Composable
private fun Board(
    state: MerchantOrderBoardUiState,
    placedAt: PlacedAtFormatter,
    onAdvance: (String) -> Unit,
    onStartReject: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.error?.let { item { ErrorRow(it, onDismissError) } }
        if (state.incoming.isEmpty() && state.inProgress.isEmpty()) {
            item { Text(stringResource(R.string.board_empty), style = MaterialTheme.typography.bodyLarge) }
        }
        if (state.incoming.isNotEmpty()) {
            item { SectionTitle(R.string.board_incoming_title) }
            items(state.incoming, key = { it.id }) { order ->
                OrderCard(order, placedAt) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val idle = order.id !in state.busy
                        Button(onClick = { onAdvance(order.id) }, enabled = idle) { Text(stringResource(R.string.board_accept)) }
                        OutlinedButton(onClick = { onStartReject(order.id) }, enabled = idle) {
                            Text(stringResource(R.string.board_reject))
                        }
                    }
                }
            }
        }
        if (state.inProgress.isNotEmpty()) {
            item { SectionTitle(R.string.board_in_progress_title) }
            items(state.inProgress, key = { it.id }) { order ->
                OrderCard(order, placedAt) { StepAction(order, enabled = order.id !in state.busy, onAdvance) }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: Int) = Text(stringResource(title), style = MaterialTheme.typography.titleLarge)

@Composable
private fun OrderCard(order: Order, placedAt: PlacedAtFormatter, actions: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(order.customerName, style = MaterialTheme.typography.titleMedium)
            Text(placedAtText(order.createdAtMillis, placedAt), style = MaterialTheme.typography.labelLarge)
            Text(itemsSummary(order), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.board_order_total, stringResource(R.string.price_nio, PriceInput.format(order.totals.total))),
                style = MaterialTheme.typography.bodyMedium,
            )
            actions()
        }
    }
}

/** The button for the merchant's next step, or just the status when the merchant has none left. */
@Composable
private fun StepAction(order: Order, enabled: Boolean, onAdvance: (String) -> Unit) {
    val label = when (OrderTransitions.merchantAdvance(order.status)) {
        OrderStatus.PREPARING -> R.string.board_start_preparing
        OrderStatus.READY -> R.string.board_mark_ready
        else -> null
    }
    if (label == null) {
        Text(stringResource(OrderStatusLabels.of(order.status)), style = MaterialTheme.typography.labelLarge)
    } else {
        Button(onClick = { onAdvance(order.id) }, enabled = enabled) { Text(stringResource(label)) }
    }
}

@Composable
private fun ErrorRow(error: OrderBoardError, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val message = if (error == OrderBoardError.LOAD_FAILED) R.string.board_error_load else R.string.board_error_action
        Text(stringResource(message), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
    }
}

@Composable
private fun RejectDialog(
    state: MerchantOrderBoardUiState,
    reasonField: ReasonField,
    onReasonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.board_reject_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasonField(state.rejectReason, onReasonChange, state.rejectReasonMissing)
                if (state.rejectReasonMissing) {
                    Text(
                        stringResource(R.string.board_reject_reason_required),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = state.rejecting !in state.busy) {
                Text(stringResource(R.string.board_reject_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun MerchantOrderBoardPreview() {
    OtliTheme {
        MerchantOrderBoardContent(
            state = MerchantOrderBoardUiState(
                isLoading = false,
                incoming = listOf(sampleOrder("o1", OrderStatus.PLACED)),
                inProgress = listOf(sampleOrder("o2", OrderStatus.ACCEPTED), sampleOrder("o3", OrderStatus.READY)),
            ),
            onAdvance = {},
            onStartReject = {},
            onRejectReasonChange = {},
            onConfirmReject = {},
            onDismissReject = {},
            onDismissError = {},
            reasonField = { _, _, _ -> Text("") },
        )
    }
}
