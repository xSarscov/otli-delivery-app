package com.otli.app.ordering.adapters.ui

import com.otli.app.core.money.Money
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderItem
import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.ordering.domain.Totals

/** What an order looks like to the product's `item x quantity` wording, shared by the order screens. */
internal fun itemsSummary(order: Order): String = order.items.joinToString(", ") { "${it.quantity} x ${it.name}" }

/** Sample data for `@Preview` only. */
internal fun sampleOrder(id: String, status: OrderStatus, rejectReason: String? = null) = Order(
    id = id,
    customerId = "customer-1",
    customerName = "Ana Lopez",
    customerPhone = "+50588880201",
    merchantId = "m1",
    merchantName = "Comedor Marta",
    pickup = OrderLocation(12.2667, -86.5667, "Frente al parque"),
    dropoff = OrderLocation(12.27, -86.57, "Casa azul"),
    items = listOf(OrderItem("p1", "Nacatamal", Money(12000), 2), OrderItem("p2", "Fresco", Money(2500), 1)),
    totals = Totals(subtotal = Money(26500), fee = Money(3000), total = Money(29500)),
    status = status,
    courierId = null,
    rejectReason = rejectReason,
    createdAtMillis = 1_000L,
)
