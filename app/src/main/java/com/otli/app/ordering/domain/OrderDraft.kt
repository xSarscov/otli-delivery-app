package com.otli.app.ordering.domain

import com.otli.app.core.money.Money

/** A pin plus the customer's written reference; the same shape serves pickup and drop-off. */
data class OrderLocation(val latitude: Double, val longitude: Double, val reference: String)

/** One ordered product, copied at placement so the order stays readable if the catalog changes. */
data class OrderItem(val productId: String, val name: String, val unitPrice: Money, val quantity: Int)

/**
 * Everything an order records at the moment of placement (design: Firestore Data Model). The
 * delivery fee inside [totals] is a snapshot: later fee changes never reach a placed order.
 * Payment is always cash, so it is not a field here.
 */
data class OrderDraft(
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val merchantId: String,
    val merchantName: String,
    val pickup: OrderLocation,
    val dropoff: OrderLocation,
    val items: List<OrderItem>,
    val totals: Totals,
)
