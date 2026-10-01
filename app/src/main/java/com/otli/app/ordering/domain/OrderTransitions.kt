package com.otli.app.ordering.domain

import com.otli.app.ordering.domain.Actor.ADMIN
import com.otli.app.ordering.domain.Actor.COURIER
import com.otli.app.ordering.domain.Actor.CUSTOMER
import com.otli.app.ordering.domain.Actor.MERCHANT
import com.otli.app.ordering.domain.OrderStatus.ACCEPTED
import com.otli.app.ordering.domain.OrderStatus.CANCELLED
import com.otli.app.ordering.domain.OrderStatus.CLAIMED
import com.otli.app.ordering.domain.OrderStatus.DELIVERED
import com.otli.app.ordering.domain.OrderStatus.PICKED_UP
import com.otli.app.ordering.domain.OrderStatus.PLACED
import com.otli.app.ordering.domain.OrderStatus.PREPARING
import com.otli.app.ordering.domain.OrderStatus.READY
import com.otli.app.ordering.domain.OrderStatus.REJECTED

/**
 * The order state machine: which actor may move an order from one status to another.
 *
 * Firestore rules are the real authority; this table lets the app offer only valid actions.
 * It must equal `backend/contracts/order-transitions.json` (asserted by `OrderTransitionsContractTest`, ADR-14).
 */
object OrderTransitions {
    val allowed: Set<Triple<OrderStatus, OrderStatus, Actor>> = setOf(
        Triple(PLACED, ACCEPTED, MERCHANT),
        Triple(PLACED, REJECTED, MERCHANT),
        Triple(PLACED, CANCELLED, CUSTOMER),
        Triple(ACCEPTED, PREPARING, MERCHANT),
        Triple(PREPARING, READY, MERCHANT),
        Triple(READY, CLAIMED, COURIER),
        Triple(CLAIMED, PICKED_UP, COURIER),
        Triple(PICKED_UP, DELIVERED, COURIER),
        Triple(CLAIMED, READY, ADMIN),
    )

    fun isAllowed(from: OrderStatus, to: OrderStatus, actor: Actor): Boolean = Triple(from, to, actor) in allowed

    /** The one forward step a merchant takes from [from] (rejecting is a separate choice), or null when they have none. */
    fun merchantAdvance(from: OrderStatus): OrderStatus? =
        OrderStatus.entries.firstOrNull { it != OrderStatus.REJECTED && isAllowed(from, it, Actor.MERCHANT) }
}
