package com.otli.app.ordering.application

import com.otli.app.core.money.Money
import com.otli.app.ordering.domain.Actor
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderDraft
import com.otli.app.ordering.domain.OrderItem
import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.ordering.domain.Totals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

fun anOrder(
    id: String = "o1",
    status: OrderStatus = OrderStatus.PLACED,
    customerId: String = "customer-1",
    merchantId: String = "m1",
    merchantName: String = "Comedor Marta",
    createdAtMillis: Long = 1_000L,
    rejectReason: String? = null,
) = Order(
    id = id,
    customerId = customerId,
    customerName = "Ana Lopez",
    customerPhone = "+50588880201",
    merchantId = merchantId,
    merchantName = merchantName,
    pickup = OrderLocation(12.2667, -86.5667, "Frente al parque"),
    dropoff = OrderLocation(12.27, -86.57, "Casa azul"),
    items = listOf(OrderItem("p1", "Nacatamal", Money(12000), 2), OrderItem("p2", "Fresco", Money(2500), 1)),
    totals = Totals(subtotal = Money(26500), fee = Money(3000), total = Money(29500)),
    status = status,
    courierId = null,
    rejectReason = rejectReason,
    createdAtMillis = createdAtMillis,
)

/**
 * In-memory orders: [orders] is the live collection every observer reads, like Firestore listeners.
 * Observers emit in insertion order, like a snapshot that arrives in no particular order: view models
 * must sort. A non-null [listenerError] makes every observer fail when collected.
 */
class FakeOrderRepository : OrderRepository {
    data class Transition(val orderId: String, val to: OrderStatus, val actor: Actor, val reason: String?)

    val placed = mutableListOf<OrderDraft>()
    var outcome: (OrderDraft) -> Result<String> = { Result.success("order-${placed.size}") }

    val orders = MutableStateFlow<List<Order>>(emptyList())
    var listenerError: Throwable? = null

    val transitions = mutableListOf<Transition>()
    var transitionOutcome: suspend (Transition) -> Result<Unit> = { Result.success(Unit) }

    override suspend fun place(draft: OrderDraft): Result<String> {
        placed += draft
        return outcome(draft)
    }

    override fun observe(orderId: String): Flow<Order?> = live { list -> list.firstOrNull { it.id == orderId } }

    override fun observeForCustomer(customerId: String): Flow<List<Order>> =
        live { list -> list.filter { it.customerId == customerId } }

    override fun observeForMerchant(merchantId: String): Flow<List<Order>> =
        live { list -> list.filter { it.merchantId == merchantId } }

    override suspend fun transition(orderId: String, to: OrderStatus, actor: Actor, reason: String?): Result<Unit> {
        val transition = Transition(orderId, to, actor, reason)
        transitions += transition
        return transitionOutcome(transition)
    }

    private fun <T> live(select: (List<Order>) -> T): Flow<T> = flow {
        listenerError?.let { throw it }
        emitAll(orders.map(select))
    }
}
