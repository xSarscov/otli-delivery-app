package com.otli.app.ordering.domain

import com.otli.app.core.money.Money

/** What the customer pays: the items, the flat delivery fee, and their sum. Invariant: total = subtotal + fee. */
data class Totals(val subtotal: Money, val fee: Money, val total: Money)

/** Prices a cart at the moment of placement (ADR-10); the fee is the one configured by the Admin then. */
object CheckoutCalculator {
    fun totals(lines: List<CartLine>, fee: Money): Totals {
        val subtotal = lines.fold(Money(0)) { sum, line -> sum + line.unitPrice * line.quantity }
        return Totals(subtotal = subtotal, fee = fee, total = subtotal + fee)
    }
}
