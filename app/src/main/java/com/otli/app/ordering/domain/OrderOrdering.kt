package com.otli.app.ordering.domain

/**
 * How order lists are ordered for every role. Snapshots arrive in no guaranteed order, so views sort here.
 *
 * Every list of orders shown in the app (merchant board sections, customer orders sections, the courier
 * pool, and any list added later) MUST be ordered through this object, and its tests must feed it
 * out-of-order input, so no list can silently fall back to the query order.
 */
object OrderOrdering {
    /**
     * Most recently placed first. An order whose server timestamp is still pending ([Order.createdAtMillis]
     * is zero) was placed a moment ago, so it goes on top. Ties fall back to the id to keep the list stable.
     */
    fun newestFirst(orders: List<Order>): List<Order> = newestFirst(orders, Order::createdAtMillis, Order::id)

    /** The same ordering for any order-like [items], given their placement time in epoch millis and their id. */
    fun <T> newestFirst(items: List<T>, placedAtMillis: (T) -> Long, id: (T) -> String): List<T> =
        items.sortedWith(compareByDescending<T> { sortKey(placedAtMillis(it)) }.thenBy(id))

    private fun sortKey(placedAtMillis: Long) = if (placedAtMillis == PENDING) Long.MAX_VALUE else placedAtMillis

    private const val PENDING = 0L
}
