package com.otli.app.ordering.adapters.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.Actor
import com.otli.app.ordering.domain.OrderStatus
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class OrderTrackingViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val orders = FakeOrderRepository()

    private fun viewModel(orderId: String? = "o1") =
        OrderTrackingViewModel(orders, SavedStateHandle(if (orderId == null) emptyMap() else mapOf("orderId" to orderId)))

    // --- watching the order ---

    @Test
    fun itStartsLoadingBeforeTheFirstSnapshot() {
        assertThat(OrderTrackingUiState().isLoading).isTrue()
    }

    @Test
    fun itShowsTheOrderAndFollowsItsStatusLive() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.order?.status).isEqualTo(OrderStatus.PLACED)

        orders.orders.value = listOf(anOrder("o1", OrderStatus.PREPARING))

        assertThat(viewModel.uiState.value.order?.status).isEqualTo(OrderStatus.PREPARING)
    }

    @Test
    fun aMissingOrderIdOrOrderIsNotFound() {
        assertThat(viewModel(orderId = null).uiState.value.notFound).isTrue()
        assertThat(viewModel(orderId = " ").uiState.value.notFound).isTrue()

        orders.listenerError = IllegalStateException("must not listen without an id")
        assertThat(viewModel(orderId = "").uiState.value.loadFailed).isFalse()
        orders.listenerError = null

        orders.orders.value = listOf(anOrder("other"))
        val state = viewModel("o1").uiState.value
        assertThat(state.notFound).isTrue()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun aFailingListenerShowsALoadFailureInsteadOfCrashing() {
        orders.listenerError = IllegalStateException("PERMISSION_DENIED")

        val state = viewModel().uiState.value

        assertThat(state.loadFailed).isTrue()
        assertThat(state.isLoading).isFalse()
    }

    // --- cancelling ---

    @Test
    fun cancelIsEnabledExactlyWhileTheOrderIsPlaced() {
        for (status in OrderStatus.entries) {
            orders.orders.value = listOf(anOrder("o1", status))

            assertThat(viewModel().uiState.value.cancelEnabled).isEqualTo(status == OrderStatus.PLACED)
        }
    }

    @Test
    fun cancelIsDisabledWhenThereIsNoOrder() {
        assertThat(viewModel().uiState.value.cancelEnabled).isFalse()
    }

    @Test
    fun cancellingMovesAPlacedOrderToCancelledAsTheCustomer() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))

        viewModel().cancel()

        assertThat(orders.transitions).containsExactly(FakeOrderRepository.Transition("o1", OrderStatus.CANCELLED, Actor.CUSTOMER, null))
    }

    @Test
    fun anOrderThatIsNoLongerPlacedCannotBeCancelled() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED))

        viewModel().cancel()

        assertThat(orders.transitions).isEmpty()
    }

    @Test
    fun cancellingTwiceWhileInFlightSendsOnceAndDisablesTheButton() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        val release = CompletableDeferred<Unit>()
        orders.transitionOutcome = {
            release.await()
            Result.success(Unit)
        }
        val viewModel = viewModel()

        viewModel.cancel()
        viewModel.cancel()
        assertThat(viewModel.uiState.value.cancelEnabled).isFalse()
        assertThat(viewModel.uiState.value.isCancelling).isTrue()

        release.complete(Unit)
        assertThat(orders.transitions).hasSize(1)
        assertThat(viewModel.uiState.value.isCancelling).isFalse()
    }

    @Test
    fun aRefusedCancellationShowsAnErrorThatCanBeDismissedAndKeepsCancelAvailable() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        orders.transitionOutcome = { Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()

        viewModel.cancel()

        assertThat(viewModel.uiState.value.error).isEqualTo(TrackingError.CANCEL_FAILED)
        assertThat(viewModel.uiState.value.cancelEnabled).isTrue()

        viewModel.dismissError()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun tryingAgainClearsThePreviousError() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        orders.transitionOutcome = { Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()
        viewModel.cancel()
        orders.transitionOutcome = { CompletableDeferred<Unit>().await().let { Result.success(Unit) } }

        viewModel.cancel()

        assertThat(viewModel.uiState.value.error).isNull()
    }
}
