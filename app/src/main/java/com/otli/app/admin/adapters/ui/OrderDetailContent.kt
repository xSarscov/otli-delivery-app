package com.otli.app.admin.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.catalog.domain.PriceInput
import com.otli.app.core.money.Money
import com.otli.app.core.theme.OtliTheme
import com.otli.app.core.ui.OtliTopBar
import com.otli.app.ordering.adapters.ui.OrderStatusLabels
import com.otli.app.ordering.adapters.ui.OrderTrackingUiState
import com.otli.app.ordering.adapters.ui.itemsSummary
import com.otli.app.ordering.adapters.ui.sampleOrder
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus

object OrderDetailTags {
    const val LOADING = "order-detail-loading"
}

/**
 * Stateless read-only detail of one order for Admin: who ordered what from whom, where it goes, how it
 * stands and, in the [liveMap] slot, where the courier is while the order is on the road. It reuses the
 * tracking state of one order; Admin acts on orders from the stuck list, not here.
 */
@Composable
fun OrderDetailContent(
    state: OrderTrackingUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    liveMap: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        OtliTopBar(title = stringResource(R.string.admin_order_detail_title), onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val order = state.order
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag(OrderDetailTags.LOADING))
                state.notFound -> Message(R.string.tracking_not_found)
                state.loadFailed || order == null -> Message(R.string.tracking_load_failed)
                else -> Details(order, liveMap)
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
private fun Details(order: Order, liveMap: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.cart_from_store, order.merchantName), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(OrderStatusLabels.of(order.status)), style = MaterialTheme.typography.headlineSmall)
        order.rejectReason?.takeIf { order.status == OrderStatus.REJECTED && it.isNotBlank() }?.let {
            Text(stringResource(R.string.tracking_reject_reason, it), color = MaterialTheme.colorScheme.error)
        }
        order.cancelReason?.takeIf { order.status == OrderStatus.CANCELLED && it.isNotBlank() }?.let {
            Text(stringResource(R.string.tracking_cancel_reason, it), color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(R.string.admin_order_customer, order.customerName), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.admin_order_phone, order.customerPhone), style = MaterialTheme.typography.bodyMedium)
        order.courierId?.let { Text(stringResource(R.string.admin_order_courier, it), style = MaterialTheme.typography.bodyMedium) }
        Text(stringResource(R.string.admin_order_pickup, order.pickup.reference), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.admin_order_dropoff, order.dropoff.reference), style = MaterialTheme.typography.bodyMedium)
        liveMap()
        HorizontalDivider()
        Text(itemsSummary(order), style = MaterialTheme.typography.bodyLarge)
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

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun OrderDetailPreview() {
    OtliTheme {
        OrderDetailContent(
            state = OrderTrackingUiState(isLoading = false, order = sampleOrder("o1", OrderStatus.CANCELLED)),
            onBack = {},
        )
    }
}
