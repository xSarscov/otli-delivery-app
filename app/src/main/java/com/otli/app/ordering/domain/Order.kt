package com.otli.app.ordering.domain

/**
 * A placed order as read back from `orders/{orderId}`. The money, items and pins are the snapshot
 * taken at placement; only [status], [courierId] and [rejectReason] change afterwards.
 */
data class Order(
    val id: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val merchantId: String,
    val merchantName: String,
    val pickup: OrderLocation,
    val dropoff: OrderLocation,
    val items: List<OrderItem>,
    val totals: Totals,
    val status: OrderStatus,
    val courierId: String?,
    val rejectReason: String?,
    /** Epoch millis; zero while the server timestamp of a just-written order is still pending. */
    val createdAtMillis: Long,
)
