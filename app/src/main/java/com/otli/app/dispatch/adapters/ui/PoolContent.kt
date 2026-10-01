package com.otli.app.dispatch.adapters.ui

import androidx.annotation.StringRes
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.otli.app.core.money.Money
import com.otli.app.core.theme.OtliTheme
import com.otli.app.dispatch.domain.PoolGate
import com.otli.app.dispatch.domain.PoolOrder
import com.otli.app.ordering.adapters.ui.PlacedAtFormatter
import com.otli.app.ordering.adapters.ui.placedAtText
import com.otli.app.ordering.adapters.ui.rememberPlacedAtFormatter
import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.Totals

object PoolTags {
    const val LOADING = "pool-loading"

    fun claim(orderId: String) = "pool-claim-$orderId"
}

/** The wording of each [PoolMessage]. */
object PoolMessages {
    @StringRes
    fun of(message: PoolMessage): Int = when (message) {
        PoolMessage.ALREADY_TAKEN -> R.string.pool_message_taken
        PoolMessage.NOT_READY -> R.string.pool_message_not_ready
        PoolMessage.BUSY -> R.string.pool_message_busy
        PoolMessage.OFFLINE -> R.string.pool_message_offline
        PoolMessage.NOT_ACTIVE -> R.string.pool_message_not_active
        PoolMessage.CLAIM_FAILED -> R.string.pool_message_failed
        PoolMessage.LOAD_FAILED -> R.string.pool_message_load
    }
}

/**
 * Stateless pool: the orders waiting for a courier while the courier is online and free. An offline
 * courier is told to go online and a courier with an active order sees nothing here at all (the
 * active delivery takes the screen).
 */
@Composable
fun PoolContent(
    state: PoolUiState,
    onClaim: (String) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
    placedAt: PlacedAtFormatter = rememberPlacedAtFormatter(),
) {
    when {
        state.isLoading -> Box(modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).testTag(PoolTags.LOADING))
        }
        state.gate == PoolGate.BUSY -> Unit
        else -> LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { item { MessageRow(it, onDismissMessage) } }
            if (state.gate == PoolGate.OFFLINE) {
                item { Text(stringResource(R.string.pool_offline), style = MaterialTheme.typography.bodyLarge) }
            } else {
                item { Text(stringResource(R.string.pool_title), style = MaterialTheme.typography.titleLarge) }
                if (state.orders.isEmpty()) {
                    item { Text(stringResource(R.string.pool_empty), style = MaterialTheme.typography.bodyLarge) }
                }
                items(state.orders, key = { it.id }) { order ->
                    OrderCard(order, placedAt, claiming = order.id in state.claiming, onClaim = { onClaim(order.id) })
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: PoolOrder, placedAt: PlacedAtFormatter, claiming: Boolean, onClaim: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(order.merchantName, style = MaterialTheme.typography.titleMedium)
            Text(placedAtText(order.createdAtMillis, placedAt), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.pool_pickup, order.pickup.reference), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.pool_dropoff, order.dropoff.reference), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.pool_fee, price(order.totals.fee)), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.pool_cash, price(order.totals.total)), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClaim, enabled = !claiming, modifier = Modifier.testTag(PoolTags.claim(order.id))) {
                Text(stringResource(R.string.pool_claim))
            }
        }
    }
}

@Composable
private fun price(amount: Money) = stringResource(R.string.price_nio, PriceInput.format(amount))

@Composable
private fun MessageRow(message: PoolMessage, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(PoolMessages.of(message)), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
    }
}

private val previewOrder = PoolOrder(
    id = "o1",
    merchantName = "Comedor Marta",
    pickup = OrderLocation(12.2667, -86.5667, "Frente al parque"),
    dropoff = OrderLocation(12.27, -86.57, "Casa azul"),
    totals = Totals(subtotal = Money(24000), fee = Money(3000), total = Money(27000)),
    readyAtMillis = 1_000L,
    createdAtMillis = 500L,
)

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun PoolOpenPreview() {
    OtliTheme {
        PoolContent(
            PoolUiState(isLoading = false, gate = PoolGate.OPEN, orders = listOf(previewOrder), message = PoolMessage.ALREADY_TAKEN),
            onClaim = {},
            onDismissMessage = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PoolOfflinePreview() {
    OtliTheme { PoolContent(PoolUiState(isLoading = false), onClaim = {}, onDismissMessage = {}) }
}
