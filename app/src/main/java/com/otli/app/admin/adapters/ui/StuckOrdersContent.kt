package com.otli.app.admin.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.otli.app.admin.domain.WaitingTime
import com.otli.app.catalog.domain.PriceInput
import com.otli.app.core.theme.OtliTheme
import com.otli.app.ordering.adapters.ui.OrderStatusLabels
import com.otli.app.ordering.adapters.ui.PlacedAtFormatter
import com.otli.app.ordering.adapters.ui.placedAtText
import com.otli.app.ordering.adapters.ui.rememberPlacedAtFormatter
import com.otli.app.ordering.adapters.ui.sampleOrder
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus

object StuckOrdersTags {
    const val LOADING = "stuck-orders-loading"

    fun row(orderId: String) = "stuck-orders-row-$orderId"

    fun release(orderId: String) = "stuck-orders-release-$orderId"

    fun cancel(orderId: String) = "stuck-orders-cancel-$orderId"
}

/**
 * Stateless list of the orders Admin can act on, in three groups: waiting for a courier (cancel), with a
 * courier (release) and in the kitchen (cancel). A claimed order offers no cancel: it is released first.
 * The [cancelDialog] slot hosts the reason dialog, because a text field inside a dialog cannot be hosted by
 * the unit tests; the default is the real [CancelReasonDialog].
 */
@Composable
fun StuckOrdersContent(
    state: StuckOrdersUiState,
    nowMillis: Long,
    onRelease: (Order) -> Unit,
    onCancel: (Order) -> Unit,
    onConfirmCancel: (String) -> Unit,
    onDismissCancel: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    placedAt: PlacedAtFormatter = rememberPlacedAtFormatter(),
    cancelDialog: @Composable (CancelDialogState) -> Unit = { CancelReasonDialog(it) },
) {
    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag(StuckOrdersTags.LOADING))
            state.loadFailed -> Message(R.string.admin_stuck_load_failed)
            else -> Orders(state, nowMillis, placedAt, onRelease, onCancel, onDismissError)
        }
        state.cancelTarget?.let { target ->
            cancelDialog(CancelDialogState(target, state.reasonInvalid, state.busyOrderId != null, onConfirmCancel, onDismissCancel))
        }
    }
}

@Composable
private fun BoxScope.Message(message: Int) {
    Text(
        stringResource(message),
        Modifier.align(Alignment.Center).padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun Orders(
    state: StuckOrdersUiState,
    nowMillis: Long,
    placedAt: PlacedAtFormatter,
    onRelease: (Order) -> Unit,
    onCancel: (Order) -> Unit,
    onDismissError: () -> Unit,
) {
    val none = state.waiting.isEmpty() && state.withCourier.isEmpty() && state.inKitchen.isEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.error != null) item(key = "error") { ErrorBanner(onDismissError) }
        if (none) item(key = "none") { Text(stringResource(R.string.admin_stuck_empty), style = MaterialTheme.typography.bodyLarge) }
        val busy = state.busyOrderId != null
        group(R.string.admin_stuck_waiting, state.waiting, nowMillis, placedAt, busy, R.string.admin_cancel_order, StuckOrdersTags::cancel, onCancel)
        group(R.string.admin_stuck_with_courier, state.withCourier, nowMillis, placedAt, busy, R.string.admin_release_claim, StuckOrdersTags::release, onRelease)
        group(R.string.admin_stuck_kitchen, state.inKitchen, nowMillis, placedAt, busy, R.string.admin_cancel_order, StuckOrdersTags::cancel, onCancel)
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.group(
    title: Int,
    orders: List<Order>,
    nowMillis: Long,
    placedAt: PlacedAtFormatter,
    busy: Boolean,
    actionLabel: Int,
    actionTag: (String) -> String,
    onAction: (Order) -> Unit,
) {
    if (orders.isEmpty()) return
    item(key = "title-$title") { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) }
    items(orders, key = { it.id }) { order ->
        OrderRow(order, nowMillis, placedAt, busy, actionLabel, actionTag(order.id)) { onAction(order) }
    }
}

@Composable
private fun OrderRow(
    order: Order,
    nowMillis: Long,
    placedAt: PlacedAtFormatter,
    busy: Boolean,
    actionLabel: Int,
    actionTag: String,
    onAction: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag(StuckOrdersTags.row(order.id))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(order.merchantName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.admin_order_customer, order.customerName), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(OrderStatusLabels.of(order.status)), style = MaterialTheme.typography.bodyLarge)
            Text(placedAtText(order.createdAtMillis, placedAt), style = MaterialTheme.typography.bodyMedium)
            WaitingTime.minutes(order.createdAtMillis, nowMillis)?.let {
                Text(stringResource(R.string.admin_order_waiting, it), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.price_nio, PriceInput.format(order.totals.total)), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val modifier = Modifier.testTag(actionTag)
                if (order.status == OrderStatus.CLAIMED) {
                    Button(onClick = onAction, enabled = !busy, modifier = modifier) { Text(stringResource(actionLabel)) }
                } else {
                    OutlinedButton(onClick = onAction, enabled = !busy, modifier = modifier) { Text(stringResource(actionLabel)) }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.admin_stuck_action_failed), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun StuckOrdersPreview() {
    OtliTheme {
        StuckOrdersContent(
            state = StuckOrdersUiState(
                isLoading = false,
                waiting = listOf(sampleOrder("w1", OrderStatus.READY)),
                withCourier = listOf(sampleOrder("c1", OrderStatus.CLAIMED)),
                inKitchen = listOf(sampleOrder("k1", OrderStatus.PREPARING)),
            ),
            nowMillis = 2_100_000L,
            onRelease = {},
            onCancel = {},
            onConfirmCancel = {},
            onDismissCancel = {},
            onDismissError = {},
        )
    }
}
