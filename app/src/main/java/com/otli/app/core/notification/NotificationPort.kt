package com.otli.app.core.notification

import com.otli.app.core.money.Money
import com.otli.app.ordering.domain.OrderStatus

/** What the user should be told about an order. The adapter decides how it is worded and shown. */
sealed interface OrderNotice {
    val orderId: String

    /** A new order reached the merchant and waits for an answer. */
    data class NewOrder(override val orderId: String, val customerName: String, val total: Money) : OrderNotice

    /** The customer's order moved to [status]; [reason] is the merchant's answer when it was rejected. */
    data class StatusChanged(
        override val orderId: String,
        val merchantName: String,
        val status: OrderStatus,
        val reason: String?,
    ) : OrderNotice
}

/**
 * Raises a notice to the user while the app runs (ADR-12: local notifications only, no server push).
 * Must never throw: a device that refuses notifications simply drops them.
 */
interface NotificationPort {
    fun notify(notice: OrderNotice)
}
