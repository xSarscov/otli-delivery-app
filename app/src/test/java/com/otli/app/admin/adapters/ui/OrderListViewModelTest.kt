package com.otli.app.admin.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.admin.application.FakeAdminRepository
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class OrderListViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val admin = FakeAdminRepository()

    private fun viewModel() = OrderListViewModel(admin)

    @Test
    fun itIsLoadingUntilTheFirstListArrives() {
        val gate = CompletableDeferred<Unit>()
        admin.beforeFirstList = { gate.await() }

        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.isLoading).isTrue()
        gate.complete(Unit)
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun ordersOfEveryMerchantAndCustomerAreListedNewestFirstWhateverTheArrivalOrder() {
        admin.allOrders.value = listOf(
            anOrder("old", OrderStatus.DELIVERED, merchantId = "m1", customerId = "c1", createdAtMillis = 100),
            anOrder("new", OrderStatus.PLACED, merchantId = "m2", customerId = "c2", createdAtMillis = 900),
            anOrder("mid", OrderStatus.CANCELLED, merchantId = "m1", customerId = "c2", createdAtMillis = 500),
        )

        val state = viewModel().uiState.value

        assertThat(state.orders.map { it.id }).containsExactly("new", "mid", "old").inOrder()
        assertThat(state.isLoading).isFalse()
        assertThat(state.loadFailed).isFalse()
    }

    @Test
    fun anOrderWhoseServerTimestampIsPendingIsOnTop() {
        admin.allOrders.value = listOf(
            anOrder("settled", OrderStatus.PLACED, createdAtMillis = 900),
            anOrder("just-placed", OrderStatus.PLACED, createdAtMillis = 0),
        )

        assertThat(viewModel().uiState.value.orders.map { it.id }).containsExactly("just-placed", "settled").inOrder()
    }

    @Test
    fun theListFollowsTheLiveData() {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.orders).isEmpty()

        admin.allOrders.value = listOf(anOrder("a", OrderStatus.PLACED, createdAtMillis = 100))
        admin.allOrders.value = listOf(anOrder("b", OrderStatus.PLACED, createdAtMillis = 200), anOrder("a", OrderStatus.ACCEPTED, createdAtMillis = 100))

        assertThat(viewModel.uiState.value.orders.map { it.id }).containsExactly("b", "a").inOrder()
        assertThat(viewModel.uiState.value.orders.last().status).isEqualTo(OrderStatus.ACCEPTED)
    }

    @Test
    fun aRejectedListenerIsReportedInsteadOfCrashing() {
        admin.listenerError = IOException("denied")

        val state = viewModel().uiState.value

        assertThat(state.loadFailed).isTrue()
        assertThat(state.isLoading).isFalse()
    }
}
