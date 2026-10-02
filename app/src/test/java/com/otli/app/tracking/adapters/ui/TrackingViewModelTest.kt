package com.otli.app.tracking.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.dispatch.application.FakeDispatchRepository
import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.FakeSignedInAuth
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.tracking.application.TrackingController
import com.otli.app.tracking.application.TrackingCoordinator
import com.otli.app.tracking.domain.TrackingPlan
import org.junit.Rule
import org.junit.Test

class TrackingViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val dispatch = FakeDispatchRepository()
    private val orders = FakeOrderRepository()
    private val calls = mutableListOf<String>()
    private val controller = object : TrackingController {
        override fun start(orderId: String, courierId: String) {
            calls += "start:$orderId"
        }

        override fun stop() {
            calls += "stop"
        }
    }

    private fun viewModel(auth: FakeSignedInAuth = FakeSignedInAuth("courier-1")) =
        TrackingViewModel(TrackingCoordinator(dispatch, orders, auth, controller))

    private fun delivering(status: OrderStatus = OrderStatus.CLAIMED) {
        orders.orders.value = listOf(anOrder("o1", status, courierId = "courier-1"))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")
    }

    @Test
    fun aCourierWithoutADeliveryHasNothingToShow() {
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = null)

        assertThat(viewModel().plan.value).isEqualTo(TrackingPlan.Idle)
    }

    @Test
    fun withoutThePermissionTheCourierIsToldTheLocationIsOff() {
        delivering()

        val viewModel = viewModel()

        assertThat(viewModel.plan.value).isEqualTo(TrackingPlan.NeedsPermission("o1"))
        assertThat(calls).doesNotContain("start:o1")
    }

    @Test
    fun grantingThePermissionStartsSharingAndRevokingItStopsIt() {
        delivering()
        val viewModel = viewModel()

        viewModel.onPermissionChanged(true)
        assertThat(viewModel.plan.value).isEqualTo(TrackingPlan.Share("o1", "courier-1"))
        assertThat(calls.last()).isEqualTo("start:o1")

        viewModel.onPermissionChanged(false)
        assertThat(viewModel.plan.value).isEqualTo(TrackingPlan.NeedsPermission("o1"))
        assertThat(calls.last()).isEqualTo("stop")
    }

    @Test
    fun theSharingEndsWhenTheOrderIsDelivered() {
        delivering(OrderStatus.PICKED_UP)
        val viewModel = viewModel()
        viewModel.onPermissionChanged(true)

        orders.orders.value = listOf(anOrder("o1", OrderStatus.DELIVERED, courierId = "courier-1"))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = null)

        assertThat(viewModel.plan.value).isEqualTo(TrackingPlan.Idle)
        assertThat(calls.last()).isEqualTo("stop")
    }

    @Test
    fun aFailingListenerDoesNotCrashAndEndsIdle() {
        delivering()
        orders.listenerError = IllegalStateException("permission denied")

        assertThat(viewModel().plan.value).isEqualTo(TrackingPlan.Idle)
    }
}
