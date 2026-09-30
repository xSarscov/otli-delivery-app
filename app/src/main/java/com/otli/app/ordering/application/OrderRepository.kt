package com.otli.app.ordering.application

import com.otli.app.ordering.domain.OrderDraft

/**
 * Port over `orders/{orderId}`. Placement is the only operation so far; observing and transitioning
 * orders arrive with the Firestore adapter (PR 3.4).
 */
interface OrderRepository {
    /** Creates the order and returns its id. Firestore rules are the final authority on the snapshot. */
    suspend fun place(draft: OrderDraft): Result<String>
}
