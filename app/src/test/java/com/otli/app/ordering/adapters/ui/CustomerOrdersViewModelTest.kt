package com.otli.app.ordering.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.FakeSignedInAuth
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Rule
import org.junit.Test

class CustomerOrdersViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val orders = FakeOrderRepository()

    private fun viewModel(auth: FakeSignedInAuth = FakeSignedInAuth("customer-1")) = CustomerOrdersViewModel(orders, auth)

    @Test
    fun itIsLoadingWhileNobodyIsSignedIn() {
        assertThat(viewModel(FakeSignedInAuth(null)).uiState.value.isLoading).isTrue()
    }

    @Test
    fun ordersInProgressAreActiveAndFinishedOnesArePastNewestFirst() {
        orders.orders.value = listOf(
            anOrder("old-delivered", OrderStatus.DELIVERED, createdAtMillis = 1_000L),
            anOrder("placed", OrderStatus.PLACED, createdAtMillis = 5_000L),
            anOrder("preparing", OrderStatus.PREPARING, createdAtMillis = 4_000L),
            anOrder("picked-up", OrderStatus.PICKED_UP, createdAtMillis = 3_000L),
            anOrder("rejected", OrderStatus.REJECTED, createdAtMillis = 2_000L),
            anOrder("cancelled", OrderStatus.CANCELLED, createdAtMillis = 6_000L),
            anOrder("someone-else", OrderStatus.PLACED, customerId = "customer-2"),
        )

        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.active.map { it.id }).containsExactly("placed", "preparing", "picked-up").inOrder()
        assertThat(state.past.map { it.id }).containsExactly("cancelled", "rejected", "old-delivered").inOrder()
    }

    @Test
    fun theNewestOrderIsFirstInBothSectionsWhateverTheSnapshotOrder() {
        orders.orders.value = listOf(
            anOrder("active-1", OrderStatus.PLACED, createdAtMillis = 1_000L),
            anOrder("active-2", OrderStatus.READY, createdAtMillis = 2_000L),
            anOrder("past-1", OrderStatus.DELIVERED, createdAtMillis = 500L),
            anOrder("past-2", OrderStatus.REJECTED, createdAtMillis = 900L),
        )

        val state = viewModel().uiState.value

        assertThat(state.active.map { it.id }).containsExactly("active-2", "active-1").inOrder()
        assertThat(state.past.map { it.id }).containsExactly("past-2", "past-1").inOrder()
    }

    @Test
    fun eachCustomerOnlySeesTheirOwnOrders() {
        orders.orders.value = listOf(
            anOrder("mine", OrderStatus.PLACED, customerId = "customer-2"),
            anOrder("theirs", OrderStatus.PLACED, customerId = "customer-1"),
        )

        val state = viewModel(FakeSignedInAuth("customer-2")).uiState.value

        assertThat(state.active.map { it.id }).containsExactly("mine")
    }

    @Test
    fun theListFollowsTheLiveOrders() {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.active).isEmpty()

        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        assertThat(viewModel.uiState.value.active.map { it.id }).containsExactly("o1")

        orders.orders.value = listOf(anOrder("o1", OrderStatus.DELIVERED))
        assertThat(viewModel.uiState.value.active).isEmpty()
        assertThat(viewModel.uiState.value.past.map { it.id }).containsExactly("o1")
    }

    @Test
    fun aFailingListenerShowsALoadFailureInsteadOfCrashing() {
        orders.listenerError = IllegalStateException("PERMISSION_DENIED")

        val state = viewModel().uiState.value

        assertThat(state.loadFailed).isTrue()
        assertThat(state.isLoading).isFalse()
    }
}
