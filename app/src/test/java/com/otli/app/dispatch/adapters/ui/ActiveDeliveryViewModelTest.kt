package com.otli.app.dispatch.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.dispatch.application.FakeDispatchRepository
import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.FakeSignedInAuth
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class ActiveDeliveryViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val dispatch = FakeDispatchRepository()
    private val orders = FakeOrderRepository()
    private val auth = FakeSignedInAuth("courier-1")

    private fun viewModel() = ActiveDeliveryViewModel(dispatch, orders, auth)

    private fun working(status: OrderStatus, id: String = "o1") {
        orders.orders.value = listOf(anOrder(id, status, courierId = "courier-1"))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = id)
    }

    // --- which order is on screen ---

    @Test
    fun itIsLoadingUntilTheSessionArrives() {
        assertThat(ActiveDeliveryViewModel(dispatch, orders, FakeSignedInAuth(null)).uiState.value.isLoading).isTrue()
    }

    @Test
    fun aFreeCourierHasNoActiveDelivery() {
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = null)
        orders.orders.value = listOf(anOrder("o1", OrderStatus.CLAIMED, courierId = "courier-1"))

        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.order).isNull()
        assertThat(state.error).isNull()
    }

    @Test
    fun theCourierSeesTheOrderTheirSlotNames() {
        working(OrderStatus.CLAIMED)
        orders.orders.value = orders.orders.value + anOrder("other", OrderStatus.CLAIMED, courierId = "courier-2")

        val state = viewModel().uiState.value

        assertThat(state.order?.id).isEqualTo("o1")
        assertThat(state.order?.status).isEqualTo(OrderStatus.CLAIMED)
    }

    @Test
    fun theOrderFollowsTheLiveDocumentFromClaimedToPickedUp() {
        working(OrderStatus.CLAIMED)
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.order?.status).isEqualTo(OrderStatus.CLAIMED)

        orders.orders.value = listOf(anOrder("o1", OrderStatus.PICKED_UP, courierId = "courier-1"))

        assertThat(viewModel.uiState.value.order?.status).isEqualTo(OrderStatus.PICKED_UP)
    }

    @Test
    fun anUnchangedSlotDoesNotRestartTheOrderListener() {
        working(OrderStatus.CLAIMED)
        val viewModel = viewModel()
        assertThat(orders.observeStarts).isEqualTo(1)

        dispatch.courier = CourierAvailability(isOnline = false, activeOrderId = "o1")

        assertThat(orders.observeStarts).isEqualTo(1)
        assertThat(viewModel.uiState.value.order?.id).isEqualTo("o1")
    }

    @Test
    fun theDeliveryDisappearsWhenTheSlotIsFreed() {
        working(OrderStatus.PICKED_UP)
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.order).isNotNull()

        orders.orders.value = listOf(anOrder("o1", OrderStatus.DELIVERED, courierId = "courier-1"))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = null)

        assertThat(viewModel.uiState.value.order).isNull()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun anActiveOrderThatCannotBeReadIsALoadError() {
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "ghost")

        val state = viewModel().uiState.value

        assertThat(state.order).isNull()
        assertThat(state.error).isEqualTo(DeliveryError.LOAD_FAILED)
    }

    @Test
    fun theLoadErrorGoesAwayOnceTheOrderArrives() {
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.error).isEqualTo(DeliveryError.LOAD_FAILED)

        orders.orders.value = listOf(anOrder("o1", OrderStatus.CLAIMED, courierId = "courier-1"))

        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.order?.id).isEqualTo("o1")
    }

    @Test
    fun aFailingOrderListenerShowsALoadErrorInsteadOfCrashing() {
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")
        orders.listenerError = IllegalStateException("PERMISSION_DENIED")

        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isEqualTo(DeliveryError.LOAD_FAILED)
    }

    @Test
    fun aFailingCourierListenerShowsALoadErrorInsteadOfCrashing() {
        dispatch.listenerError = IllegalStateException("PERMISSION_DENIED")

        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isEqualTo(DeliveryError.LOAD_FAILED)
    }

    // --- what the buttons allow ---

    @Test
    fun onlyAClaimedOrderCanBePickedUp() {
        OrderStatus.entries.forEach { status ->
            working(status)
            val state = viewModel().uiState.value

            assertThat(state.canPickUp).isEqualTo(status == OrderStatus.CLAIMED)
        }
    }

    @Test
    fun onlyAPickedUpOrderCanBeDelivered() {
        OrderStatus.entries.forEach { status ->
            working(status)
            val state = viewModel().uiState.value

            assertThat(state.canDeliver).isEqualTo(status == OrderStatus.PICKED_UP)
        }
    }

    @Test
    fun withoutAnOrderNothingCanBeDone() {
        dispatch.courier = CourierAvailability(true, null)

        val state = viewModel().uiState.value

        assertThat(state.canPickUp).isFalse()
        assertThat(state.canDeliver).isFalse()
    }

    // --- pickup ---

    @Test
    fun pickingUpAsksTheRepositoryForTheActiveOrderAndTheSignedInCourier() {
        working(OrderStatus.CLAIMED)

        viewModel().pickUp()

        assertThat(dispatch.pickedUp).containsExactly("o1" to "courier-1")
        assertThat(dispatch.delivered).isEmpty()
    }

    @Test
    fun anOrderThatIsNotClaimedCannotBePickedUp() {
        working(OrderStatus.PICKED_UP)

        viewModel().pickUp()

        assertThat(dispatch.pickedUp).isEmpty()
    }

    @Test
    fun aSecondTapWhileThePickupIsInFlightIsIgnored() {
        working(OrderStatus.CLAIMED)
        val release = CompletableDeferred<Unit>()
        dispatch.pickUpOutcome = { _, _ ->
            release.await()
            Result.success(Unit)
        }
        val viewModel = viewModel()

        viewModel.pickUp()
        viewModel.pickUp()
        assertThat(viewModel.uiState.value.isBusy).isTrue()
        assertThat(viewModel.uiState.value.canPickUp).isFalse()

        release.complete(Unit)
        assertThat(dispatch.pickedUp).hasSize(1)
        assertThat(viewModel.uiState.value.isBusy).isFalse()
    }

    // --- delivery ---

    @Test
    fun deliveringAsksTheRepositoryForTheActiveOrderAndTheSignedInCourier() {
        working(OrderStatus.PICKED_UP)

        viewModel().deliver()

        assertThat(dispatch.delivered).containsExactly("o1" to "courier-1")
        assertThat(dispatch.pickedUp).isEmpty()
    }

    @Test
    fun anOrderThatIsNotPickedUpCannotBeDelivered() {
        working(OrderStatus.CLAIMED)

        viewModel().deliver()

        assertThat(dispatch.delivered).isEmpty()
    }

    @Test
    fun aSecondTapWhileTheDeliveryIsInFlightIsIgnored() {
        working(OrderStatus.PICKED_UP)
        val release = CompletableDeferred<Unit>()
        dispatch.deliverOutcome = { _, _ ->
            release.await()
            Result.success(Unit)
        }
        val viewModel = viewModel()

        viewModel.deliver()
        viewModel.deliver()
        assertThat(viewModel.uiState.value.canDeliver).isFalse()

        release.complete(Unit)
        assertThat(dispatch.delivered).hasSize(1)
    }

    @Test
    fun nothingIsSentWithoutAnActiveOrder() {
        dispatch.courier = CourierAvailability(true, null)
        val viewModel = viewModel()

        viewModel.pickUp()
        viewModel.deliver()

        assertThat(dispatch.pickedUp).isEmpty()
        assertThat(dispatch.delivered).isEmpty()
    }

    // --- failures ---

    @Test
    fun aRefusedStepShowsAnErrorThatCanBeDismissed() {
        working(OrderStatus.CLAIMED)
        dispatch.pickUpOutcome = { _, _ -> Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()

        viewModel.pickUp()

        assertThat(viewModel.uiState.value.error).isEqualTo(DeliveryError.ACTION_FAILED)
        assertThat(viewModel.uiState.value.isBusy).isFalse()

        viewModel.dismissError()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun aRefusedDeliveryShowsAnError() {
        working(OrderStatus.PICKED_UP)
        dispatch.deliverOutcome = { _, _ -> Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()

        viewModel.deliver()

        assertThat(viewModel.uiState.value.error).isEqualTo(DeliveryError.ACTION_FAILED)
        assertThat(viewModel.uiState.value.order?.status).isEqualTo(OrderStatus.PICKED_UP)
    }

    @Test
    fun tryingAgainClearsThePreviousError() {
        working(OrderStatus.CLAIMED)
        dispatch.pickUpOutcome = { _, _ -> Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()
        viewModel.pickUp()

        val release = CompletableDeferred<Unit>()
        dispatch.pickUpOutcome = { _, _ ->
            release.await()
            Result.success(Unit)
        }
        viewModel.pickUp()

        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun anActionErrorSurvivesTheOrderUpdatingUnderneathIt() {
        working(OrderStatus.CLAIMED)
        dispatch.pickUpOutcome = { _, _ -> Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()
        viewModel.pickUp()

        orders.orders.value = listOf(anOrder("o1", OrderStatus.CLAIMED, courierId = "courier-1", merchantName = "Comedor Marta II"))

        assertThat(viewModel.uiState.value.error).isEqualTo(DeliveryError.ACTION_FAILED)
    }
}
