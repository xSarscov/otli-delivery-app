package com.otli.app.ordering.adapters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.catalog.domain.PriceInput
import com.otli.app.core.money.Money
import com.otli.app.core.theme.OtliTheme
import com.otli.app.core.ui.OtliTopBar
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus

object OrderTrackingTags {
    const val LOADING = "order-tracking-loading"
}

/**
 * Stateless tracking of one order: its status, the journey so far, what was ordered, and cancel. The
 * [liveMap] slot hosts the courier map, which shows itself only while the courier is on the way.
 */
@Composable
fun OrderTrackingContent(
    state: OrderTrackingUiState,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    liveMap: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        OtliTopBar(title = stringResource(R.string.tracking_title), onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val order = state.order
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag(OrderTrackingTags.LOADING))
                state.notFound -> Message(R.string.tracking_not_found)
                state.loadFailed || order == null -> Message(R.string.tracking_load_failed)
                else -> OrderDetails(order, state, onCancel, onDismissError, liveMap)
            }
        }
    }
}

@Composable
private fun Message(message: Int) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun OrderDetails(
    order: Order,
    state: OrderTrackingUiState,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    liveMap: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.error != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tracking_cancel_failed), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDismissError) { Text(stringResource(R.string.action_dismiss)) }
            }
        }
        Text(stringResource(R.string.cart_from_store, order.merchantName), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(OrderStatusLabels.of(order.status)), style = MaterialTheme.typography.headlineSmall)
        val reason = order.rejectReason
        if (order.status == OrderStatus.REJECTED && !reason.isNullOrBlank()) {
            Text(stringResource(R.string.tracking_reject_reason, reason), color = MaterialTheme.colorScheme.error)
        }
        Timeline(order.status)
        liveMap()
        HorizontalDivider()
        Text(itemsSummary(order), style = MaterialTheme.typography.bodyLarge)
        Totals(order)
        Text(order.dropoff.reference, style = MaterialTheme.typography.bodyMedium)
        CancelSection(order.status, state, onCancel)
    }
}

@Composable
private fun Timeline(status: OrderStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (step in OrderTimeline.of(status)) {
            val progress = when (step.progress) {
                StepProgress.DONE -> R.string.tracking_step_done
                StepProgress.CURRENT -> R.string.tracking_step_current
                StepProgress.UPCOMING -> R.string.tracking_step_upcoming
            }
            val description = stringResource(progress)
            Row(
                Modifier.semantics(mergeDescendants = true) { stateDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val color = if (step.progress == StepProgress.UPCOMING) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary
                Box(Modifier.size(if (step.progress == StepProgress.CURRENT) 18.dp else 12.dp).background(color, CircleShape))
                Text(
                    stringResource(OrderStatusLabels.step(step.status)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (step.progress == StepProgress.CURRENT) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun Totals(order: Order) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AmountRow(R.string.cart_subtotal, order.totals.subtotal)
        AmountRow(R.string.checkout_fee, order.totals.fee)
        AmountRow(R.string.checkout_total, order.totals.total, emphasized = true)
    }
}

@Composable
private fun AmountRow(label: Int, amount: Money, emphasized: Boolean = false) {
    val style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = style)
        Text(stringResource(R.string.price_nio, PriceInput.format(amount)), style = style)
    }
}

/** Cancel exists until the order is finished, but only works while the store has not answered. */
@Composable
private fun CancelSection(status: OrderStatus, state: OrderTrackingUiState, onCancel: () -> Unit) {
    if (status.isTerminal) return
    Button(onClick = onCancel, enabled = state.cancelEnabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.tracking_cancel))
    }
    if (status != OrderStatus.PLACED) {
        Text(stringResource(R.string.tracking_cancel_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun OrderTrackingPreview() {
    OtliTheme {
        OrderTrackingContent(
            state = OrderTrackingUiState(isLoading = false, order = sampleOrder("o1", OrderStatus.PREPARING)),
            onCancel = {},
            onDismissError = {},
            onBack = {},
        )
    }
}
