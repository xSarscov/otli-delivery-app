package com.otli.app.ordering.domain

import com.google.common.truth.Truth.assertThat
import com.otli.app.ordering.application.anOrder
import org.junit.Test

class OrderOrderingTest {
    private fun ids(orders: List<Order>) = OrderOrdering.newestFirst(orders).map { it.id }

    @Test
    fun theMostRecentlyPlacedOrderComesFirstWhateverTheInputOrder() {
        val oldest = anOrder("a", createdAtMillis = 1_000L)
        val middle = anOrder("b", createdAtMillis = 2_000L)
        val newest = anOrder("c", createdAtMillis = 3_000L)

        assertThat(ids(listOf(oldest, newest, middle))).containsExactly("c", "b", "a").inOrder()
        assertThat(ids(listOf(middle, oldest, newest))).containsExactly("c", "b", "a").inOrder()
    }

    @Test
    fun ordersPlacedAtTheSameInstantAreOrderedByIdSoTheListIsStable() {
        val one = anOrder("o1", createdAtMillis = 5_000L)
        val two = anOrder("o2", createdAtMillis = 5_000L)

        assertThat(ids(listOf(two, one))).containsExactly("o1", "o2").inOrder()
        assertThat(ids(listOf(one, two))).containsExactly("o1", "o2").inOrder()
    }

    @Test
    fun anOrderWhoseServerTimestampIsStillPendingIsTheNewest() {
        val pending = anOrder("pending", createdAtMillis = 0L)
        val settled = anOrder("settled", createdAtMillis = 9_000L)

        assertThat(ids(listOf(settled, pending))).containsExactly("pending", "settled").inOrder()
    }

    private data class Item(val key: String, val placedAt: Long)

    private fun keys(items: List<Item>) = OrderOrdering.newestFirst(items, Item::placedAt, Item::key).map { it.key }

    @Test
    fun anyListOfPlacedThingsFollowsTheSameRulesAsOrders() {
        assertThat(keys(listOf(Item("a", 1L), Item("c", 3L), Item("b", 2L)))).containsExactly("c", "b", "a").inOrder()
        assertThat(keys(listOf(Item("z", 5L), Item("y", 5L)))).containsExactly("y", "z").inOrder()
        assertThat(keys(listOf(Item("old", 9L), Item("pending", 0L)))).containsExactly("pending", "old").inOrder()
    }

    @Test
    fun anEmptyListStaysEmptyAndTheInputIsNotMutated() {
        assertThat(OrderOrdering.newestFirst(emptyList())).isEmpty()

        val input = listOf(anOrder("a", createdAtMillis = 1L), anOrder("b", createdAtMillis = 2L))
        OrderOrdering.newestFirst(input)
        assertThat(input.map { it.id }).containsExactly("a", "b").inOrder()
    }
}
