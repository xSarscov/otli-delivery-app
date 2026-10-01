package com.otli.app.ordering.domain

/** How order lists are ordered for every role. Snapshots arrive in no guaranteed order, so views sort here. */
object OrderOrdering {
    /**
     * Most recently placed first. An order whose server timestamp is still pending ([Order.createdAtMillis]
     * is zero) was placed a moment ago, so it goes on top. Ties fall back to the id to keep the list stable.
     */
    private val newestFirst: Comparator<Order> =
        compareByDescending<Order> { if (it.createdAtMillis == PENDING) Long.MAX_VALUE else it.createdAtMillis }
            .thenBy { it.id }

    fun newestFirst(orders: List<Order>): List<Order> = orders.sortedWith(newestFirst)

    private const val PENDING = 0L
}
