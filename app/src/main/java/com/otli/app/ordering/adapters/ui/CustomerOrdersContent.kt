package com.otli.app.ordering.adapters.ui

import androidx.compose.foundation.clickable
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
import com.otli.app.core.ui.OtliTopBar
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus

object CustomerOrdersTags {
    const val LOADING = "customer-orders-loading"
}

/** Stateless list of a customer's orders: the ones under way first, then the finished ones. */
@Composable
fun CustomerOrdersContent(
    state: CustomerOrdersUiState,
    onOrderClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    placedAt: PlacedAtFormatter = rememberPlacedAtFormatter(),
) {
    Column(modifier.fillMaxSize()) {
        OtliTopBar(title = stringResource(R.string.my_orders_title), onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag(CustomerOrdersTags.LOADING))
                state.loadFailed -> Message(R.string.my_orders_load_failed)
                state.active.isEmpty() && state.past.isEmpty() -> Message(R.string.my_orders_empty)
                else -> OrderList(state, placedAt, onOrderClick)
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
private fun OrderList(state: CustomerOrdersUiState, placedAt: PlacedAtFormatter, onOrderClick: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        section(R.string.my_orders_active, state.active, placedAt, onOrderClick)
        section(R.string.my_orders_past, state.past, placedAt, onOrderClick)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: Int,
    orders: List<Order>,
    placedAt: PlacedAtFormatter,
    onOrderClick: (String) -> Unit,
) {
    if (orders.isEmpty()) return
    item(key = "title-$title") { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) }
    items(orders, key = { it.id }) { order -> OrderRow(order, placedAt) { onOrderClick(order.id) } }
}

@Composable
private fun OrderRow(order: Order, placedAt: PlacedAtFormatter, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(order.merchantName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(OrderStatusLabels.of(order.status)), style = MaterialTheme.typography.bodyLarge)
            Text(placedAtText(order, placedAt), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(itemsSummary(order), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.price_nio, PriceInput.format(order.totals.total)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun CustomerOrdersPreview() {
    OtliTheme {
        CustomerOrdersContent(
            state = CustomerOrdersUiState(
                isLoading = false,
                active = listOf(sampleOrder("o1", OrderStatus.PREPARING)),
                past = listOf(sampleOrder("o2", OrderStatus.DELIVERED)),
            ),
            onOrderClick = {},
            onBack = {},
        )
    }
}
