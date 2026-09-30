package com.otli.app.ordering.adapters.ui

import com.otli.app.core.map.MapPin
import com.otli.app.core.money.Money
import com.otli.app.ordering.application.PlaceOrderRejection

/** Why the last attempt to place the order did not go through; nothing is written for any of these. */
sealed interface CheckoutError {
    data object PinRequired : CheckoutError

    data object ReferenceRequired : CheckoutError

    data object CashNotConfirmed : CheckoutError

    /** The delivery fee changed since the total was shown; the screen now shows [newFee] and asks again. */
    data class FeeChanged(val newFee: Money) : CheckoutError

    data object ProfileUnavailable : CheckoutError

    data class Rejected(val reason: PlaceOrderRejection) : CheckoutError
}

data class CheckoutUiState(
    val merchantName: String? = null,
    val subtotal: Money = Money(0),
    /** The fee shown to the customer; null until it could be read. */
    val fee: Money? = null,
    val pin: MapPin? = null,
    val reference: String = "",
    val cashConfirmed: Boolean = false,
    val isPlacing: Boolean = false,
    val isCartEmpty: Boolean = true,
    val error: CheckoutError? = null,
    /** Set once the order exists; the screen navigates to its tracking and the cart is already empty. */
    val placedOrderId: String? = null,
) {
    val total: Money? get() = fee?.let { subtotal + it }
}

sealed interface CheckoutFormResult {
    /** The pin and the trimmed reference the order will carry. */
    data class Valid(val pin: MapPin, val reference: String) : CheckoutFormResult

    data class Invalid(val error: CheckoutError) : CheckoutFormResult
}

/** The checks on what the customer typed, in the order the fields appear on screen. */
object CheckoutForm {
    fun validate(pin: MapPin?, reference: String, cashConfirmed: Boolean): CheckoutFormResult {
        if (pin == null) return CheckoutFormResult.Invalid(CheckoutError.PinRequired)
        val trimmed = reference.trim()
        if (trimmed.isEmpty()) return CheckoutFormResult.Invalid(CheckoutError.ReferenceRequired)
        if (!cashConfirmed) return CheckoutFormResult.Invalid(CheckoutError.CashNotConfirmed)
        return CheckoutFormResult.Valid(pin, trimmed)
    }
}
