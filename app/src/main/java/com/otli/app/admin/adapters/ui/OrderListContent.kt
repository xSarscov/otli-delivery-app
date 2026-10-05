package com.otli.app.admin.adapters.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.otli.app.core.theme.OtliTheme
import com.otli.app.ordering.adapters.ui.OrderStatusLabels
import com.otli.app.ordering.adapters.ui.PlacedAtFormatter
import com.otli.app.ordering.adapters.ui.placedAtText
import com.otli.app.ordering.adapters.ui.rememberPlacedAtFormatter
import com.otli.app.ordering.adapters.ui.sampleOrder
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus

object OrderListTags {
    const val LOADING = "order-list-loading"

    fun row(orderId: String) = "order-list-row-$orderId"
}

/** Stateless Admin list of every order, in the order the state delivers it (newest first); tapping one opens its detail. */
@Composable
fun OrderListContent(
    state: OrderListUiState,
    onOrderClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    placedAt: PlacedAtFormatter = rememberPlacedAtFormatter(),
) {
    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag(OrderListTags.LOADING))
            state.loadFailed -> Message(R.string.admin_orders_load_failed)
            state.orders.isEmpty() -> Message(R.string.admin_orders_empty)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.orders, key = { it.id }) { order -> OrderRow(order, placedAt) { onOrderClick(order.id) } }
            }
        }
    }
}

@Composable
private fun BoxScope.Message(message: Int) {
    Text(stringResource(message), Modifier.align(Alignment.Center).padding(24.dp), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun OrderRow(order: Order, placedAt: PlacedAtFormatter, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag(OrderListTags.row(order.id)).clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(order.merchantName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.admin_order_customer, order.customerName), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(OrderStatusLabels.of(order.status)), style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(placedAtText(order.createdAtMillis, placedAt), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.price_nio, PriceInput.format(order.totals.total)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun OrderListPreview() {
    OtliTheme {
        OrderListContent(
            state = OrderListUiState(
                isLoading = false,
                orders = listOf(sampleOrder("o1", OrderStatus.PLACED), sampleOrder("o2", OrderStatus.DELIVERED)),
            ),
            onOrderClick = {},
        )
    }
}
