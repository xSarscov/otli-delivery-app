package com.otli.app.dispatch.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.otli.app.core.theme.OtliTheme
import com.otli.app.ordering.adapters.ui.OrderStatusLabels
import com.otli.app.ordering.adapters.ui.itemsSummary
import com.otli.app.ordering.adapters.ui.sampleOrder
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.OrderStatus
import java.util.Locale

object DeliveryTags {
    const val LOADING = "delivery-loading"
}

/**
 * Stateless active delivery: where to pick the order up, where to take it, who to hand it to and the
 * cash to collect, with the two step buttons enabled by the order's current status. Shows nothing
 * when the courier has no active order, so the pool can take the screen.
 */
@Composable
fun ActiveDeliveryContent(
    state: ActiveDeliveryUiState,
    onPickUp: () -> Unit,
    onDeliver: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).testTag(DeliveryTags.LOADING))
        }
        state.order == null && state.error == null -> Unit
        else -> Column(
            modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { ErrorRow(it, onDismissError) }
            state.order?.let { DeliveryCard(it, state, onPickUp, onDeliver) }
        }
    }
}

@Composable
private fun DeliveryCard(order: Order, state: ActiveDeliveryUiState, onPickUp: () -> Unit, onDeliver: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.delivery_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(stepText(order.status)), style = MaterialTheme.typography.labelLarge)
            Text(order.merchantName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.delivery_pickup, order.pickup.reference), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.delivery_dropoff, order.dropoff.reference), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.delivery_pin, coordinates(order.dropoff)), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.delivery_customer, order.customerName, order.customerPhone),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(itemsSummary(order), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.delivery_cash, stringResource(R.string.price_nio, PriceInput.format(order.totals.total))),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPickUp, enabled = state.canPickUp) { Text(stringResource(R.string.delivery_pick_up)) }
                Button(onClick = onDeliver, enabled = state.canDeliver) { Text(stringResource(R.string.delivery_deliver)) }
            }
        }
    }
}

private fun stepText(status: OrderStatus): Int = when (status) {
    OrderStatus.CLAIMED -> R.string.delivery_step_claimed
    OrderStatus.PICKED_UP -> R.string.delivery_step_picked_up
    else -> OrderStatusLabels.of(status)
}

/** The dropoff pin as read-only text: the courier navigates with whatever map app they prefer. */
private fun coordinates(location: OrderLocation): String =
    String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude)

@Composable
private fun ErrorRow(error: DeliveryError, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val message = if (error == DeliveryError.LOAD_FAILED) R.string.delivery_error_load else R.string.delivery_error_action
        Text(stringResource(message), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun ActiveDeliveryClaimedPreview() {
    OtliTheme {
        ActiveDeliveryContent(
            ActiveDeliveryUiState(isLoading = false, order = sampleOrder("o1", OrderStatus.CLAIMED)),
            onPickUp = {},
            onDeliver = {},
            onDismissError = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun ActiveDeliveryPickedUpPreview() {
    OtliTheme {
        ActiveDeliveryContent(
            ActiveDeliveryUiState(isLoading = false, order = sampleOrder("o1", OrderStatus.PICKED_UP)),
            onPickUp = {},
            onDeliver = {},
            onDismissError = {},
        )
    }
}
