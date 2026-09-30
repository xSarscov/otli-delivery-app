package com.otli.app.ordering.application

import com.otli.app.ordering.domain.Actor
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderDraft
import com.otli.app.ordering.domain.OrderStatus
import kotlinx.coroutines.flow.Flow

/**
 * Port over `orders/{orderId}`. Every observer is a live listener: a failing listener ends its
 * flow with an error, so collectors must handle it (`catch`). The Firestore rules are the final
 * authority on every write.
 */
interface OrderRepository {
    /** Creates the order and returns its id. */
    suspend fun place(draft: OrderDraft): Result<String>

    /** The order, or null when it does not exist (or is not readable by this user). */
    fun observe(orderId: String): Flow<Order?>

    /** A customer's own orders, newest first. */
    fun observeForCustomer(customerId: String): Flow<List<Order>>

    /** The orders addressed to a merchant, newest first. */
    fun observeForMerchant(merchantId: String): Flow<List<Order>>

    /**
     * Moves the order to [to] as [actor]. Rejecting needs a non-blank [reason]. Fails when the
     * write is refused, e.g. an invalid transition for that actor or a stale status.
     */
    suspend fun transition(orderId: String, to: OrderStatus, actor: Actor, reason: String? = null): Result<Unit>
}
