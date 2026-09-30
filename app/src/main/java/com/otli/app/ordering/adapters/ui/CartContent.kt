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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.catalog.domain.PriceInput
import com.otli.app.core.money.Money
import com.otli.app.core.theme.OtliTheme
import com.otli.app.core.ui.OtliTopBar
import com.otli.app.ordering.application.PendingReplacement

/** Stateless cart: the store, each line with its quantity controls, and the subtotal above Checkout. */
@Composable
fun CartContent(
    state: CartUiState,
    onIncrease: (String) -> Unit,
    onDecrease: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCheckout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        OtliTopBar(title = stringResource(R.string.cart_title), onBack = onBack)
        if (state.isEmpty) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.cart_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.cart_from_store, state.merchantName.orEmpty()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(state.lines, key = { it.productId }) { line ->
                    CartLineRow(line, onIncrease, onDecrease, onRemove)
                }
            }
            CartFooter(state.subtotal, onCheckout)
        }
    }
}

@Composable
private fun CartLineRow(
    line: CartLineUi,
    onIncrease: (String) -> Unit,
    onDecrease: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.price_nio, PriceInput.format(line.lineTotal)))
            }
            val decrease = stringResource(R.string.cart_decrease, line.name)
            IconButton(onClick = { onDecrease(line.productId) }, Modifier.semantics { contentDescription = decrease }) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(line.quantity.toString(), style = MaterialTheme.typography.titleMedium)
            val increase = stringResource(R.string.cart_increase, line.name)
            IconButton(onClick = { onIncrease(line.productId) }, Modifier.semantics { contentDescription = increase }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
            IconButton(onClick = { onRemove(line.productId) }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cart_remove, line.name))
            }
        }
    }
}

@Composable
private fun CartFooter(subtotal: Money, onCheckout: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.cart_subtotal), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.price_nio, PriceInput.format(subtotal)), style = MaterialTheme.typography.titleMedium)
        }
        Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cart_checkout)) }
    }
}

/** The merchant-conflict question: confirming drops the current cart, cancelling keeps it. */
@Composable
fun ReplaceCartDialog(pending: PendingReplacement, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.replace_cart_title)) },
        text = {
            Text(stringResource(R.string.replace_cart_message, pending.current.name, pending.product.name, pending.merchant.name))
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.replace_cart_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun CartContentPreview() {
    OtliTheme {
        CartContent(
            state = CartUiState(
                merchantName = "Comedor Marta",
                lines = listOf(
                    CartLineUi("p1", "Nacatamal", Money(12050), 2, Money(24100)),
                    CartLineUi("p2", "Fresco", Money(2500), 1, Money(2500)),
                ),
                subtotal = Money(26600),
                itemCount = 3,
            ),
            onIncrease = {},
            onDecrease = {},
            onRemove = {},
            onCheckout = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CartEmptyPreview() {
    OtliTheme { CartContent(CartUiState(), {}, {}, {}, {}, {}) }
}
