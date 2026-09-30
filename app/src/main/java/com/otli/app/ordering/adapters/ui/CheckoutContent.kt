package com.otli.app.ordering.adapters.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.catalog.domain.PriceInput
import com.otli.app.core.map.MapPin
import com.otli.app.core.map.MapPinPicker
import com.otli.app.core.map.NativePinMapContent
import com.otli.app.core.map.PinMapContent
import com.otli.app.core.money.Money
import com.otli.app.core.theme.OtliTheme
import com.otli.app.core.ui.OtliTopBar
import com.otli.app.ordering.application.PlaceOrderRejection

/**
 * Stateless checkout: what the customer pays, where to deliver (map pin plus a written reference),
 * the cash confirmation, and the reason the last attempt did not go through. The map is a slot so
 * JVM tests can replace the native MapLibre view.
 */
@Composable
fun CheckoutContent(
    state: CheckoutUiState,
    onPinChange: (MapPin?) -> Unit,
    onReferenceChange: (String) -> Unit,
    onCashConfirmedChange: (Boolean) -> Unit,
    onPlace: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: PinMapContent = NativePinMapContent,
) {
    Column(modifier.fillMaxSize()) {
        OtliTopBar(title = stringResource(R.string.checkout_title), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.cart_from_store, state.merchantName.orEmpty()), style = MaterialTheme.typography.titleMedium)
            Summary(state)
            Text(stringResource(R.string.checkout_location_title), style = MaterialTheme.typography.titleMedium)
            MapPinPicker(pin = state.pin, onPinChange = onPinChange, mapContent = mapContent)
            OutlinedTextField(
                value = state.reference,
                onValueChange = onReferenceChange,
                label = { Text(stringResource(R.string.checkout_reference_label)) },
                supportingText = { Text(stringResource(R.string.checkout_reference_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            CashConfirmation(state.cashConfirmed, onCashConfirmedChange)
            state.error?.let {
                Text(errorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onPlace,
                enabled = !state.isPlacing && !state.isCartEmpty,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.checkout_place)) }
        }
    }
}

@Composable
private fun Summary(state: CheckoutUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AmountRow(R.string.cart_subtotal, stringResource(R.string.price_nio, PriceInput.format(state.subtotal)))
        AmountRow(
            R.string.checkout_fee,
            state.fee?.let { stringResource(R.string.price_nio, PriceInput.format(it)) } ?: stringResource(R.string.checkout_fee_unknown),
        )
        state.total?.let {
            HorizontalDivider()
            AmountRow(R.string.checkout_total, stringResource(R.string.price_nio, PriceInput.format(it)), emphasized = true)
        }
    }
}

@Composable
private fun AmountRow(label: Int, amount: String, emphasized: Boolean = false) {
    val style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = style)
        Text(amount, style = style)
    }
}

@Composable
private fun CashConfirmation(confirmed: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = confirmed, onCheckedChange = onChange)
        Text(
            stringResource(R.string.checkout_cash_label),
            Modifier.clickable { onChange(!confirmed) }.padding(start = 8.dp),
        )
    }
}

@Composable
private fun errorText(error: CheckoutError): String = when (error) {
    CheckoutError.PinRequired -> stringResource(R.string.checkout_error_pin)
    CheckoutError.ReferenceRequired -> stringResource(R.string.checkout_error_reference)
    CheckoutError.CashNotConfirmed -> stringResource(R.string.checkout_error_cash)
    CheckoutError.ProfileUnavailable -> stringResource(R.string.checkout_error_profile)
    is CheckoutError.FeeChanged -> stringResource(R.string.checkout_error_fee_changed, PriceInput.format(error.newFee))
    is CheckoutError.Rejected -> when (val reason = error.reason) {
        PlaceOrderRejection.EmptyCart -> stringResource(R.string.checkout_error_empty)
        is PlaceOrderRejection.TooManyItems -> stringResource(R.string.checkout_error_too_many_items, reason.max)
        is PlaceOrderRejection.StoreClosed -> stringResource(R.string.checkout_error_store_closed, reason.merchantName)
        is PlaceOrderRejection.StoreUnavailable -> stringResource(R.string.checkout_error_store_unavailable, reason.merchantName)
        is PlaceOrderRejection.ItemsUnavailable ->
            stringResource(R.string.checkout_error_items_unavailable, reason.names.joinToString(", "))
        PlaceOrderRejection.FeeUnavailable -> stringResource(R.string.checkout_error_fee_unavailable)
        PlaceOrderRejection.PlacementFailed -> stringResource(R.string.checkout_error_failed)
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun CheckoutContentPreview() {
    OtliTheme {
        CheckoutContent(
            state = CheckoutUiState(
                merchantName = "Comedor Marta",
                subtotal = Money(26500),
                fee = Money(3000),
                reference = "Casa azul, dos cuadras al sur del parque",
                pin = MapPin(12.2656, -86.5664),
                isCartEmpty = false,
                error = CheckoutError.CashNotConfirmed,
            ),
            onPinChange = {},
            onReferenceChange = {},
            onCashConfirmedChange = {},
            onPlace = {},
            onBack = {},
            mapContent = { _, _ -> Text("map") },
        )
    }
}
