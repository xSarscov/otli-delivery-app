package com.otli.app.tracking.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.dispatch.application.FakeDispatchRepository
import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.FakeSignedInAuth
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.tracking.application.FakeLocationSettingsChecker
import com.otli.app.tracking.application.TrackingController
import com.otli.app.tracking.application.TrackingCoordinator
import com.otli.app.tracking.domain.LocationAction
import com.otli.app.tracking.domain.TrackingPlan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    private val settings = FakeLocationSettingsChecker()

    private fun viewModel(auth: FakeSignedInAuth = FakeSignedInAuth("courier-1")) =
        TrackingViewModel(TrackingCoordinator(dispatch, orders, auth, controller), settings)

    private fun TestScope.collectEvents(viewModel: TrackingViewModel): List<LocationAction> {
        val events = mutableListOf<LocationAction>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        return events
    }

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

    // --- the device location switch ---

    @Test
    fun withTheLocationServicesOffTheCourierIsToldAndTheServiceStaysUp() {
        delivering()
        settings.enabled.value = false
        val viewModel = viewModel()

        viewModel.onPermissionChanged(true)

        assertThat(viewModel.plan.value).isEqualTo(TrackingPlan.ServicesOff("o1", "courier-1"))
        assertThat(calls.last()).isEqualTo("start:o1")
    }

    @Test
    fun turningTheLocationServicesBackOnResumesSharing() {
        delivering()
        val viewModel = viewModel()
        viewModel.onPermissionChanged(true)
        settings.enabled.value = false
        assertThat(viewModel.plan.value).isEqualTo(TrackingPlan.ServicesOff("o1", "courier-1"))

        settings.enabled.value = true

        assertThat(viewModel.plan.value).isEqualTo(TrackingPlan.Share("o1", "courier-1"))
    }

    @Test
    fun aBrokenLocationSettingsServiceDoesNotNagTheCourierNorCrash() {
        delivering()
        settings.failure = IllegalStateException("no location manager")
        val viewModel = viewModel()

        viewModel.onPermissionChanged(true)

        assertThat(viewModel.plan.value).isEqualTo(TrackingPlan.Share("o1", "courier-1"))
    }

    // --- prompting ---

    @Test
    fun goingOnlineWithoutThePermissionAsksForItAndThenForTheLocationServices() = runTest {
        settings.enabled.value = false
        val viewModel = viewModel()
        val events = collectEvents(viewModel)

        viewModel.onWentOnline()
        assertThat(events).containsExactly(LocationAction.REQUEST_PERMISSION)

        viewModel.onPermissionChanged(true)
        assertThat(events).containsExactly(LocationAction.REQUEST_PERMISSION, LocationAction.TURN_ON_SERVICES).inOrder()
    }

    @Test
    fun goingOnlineWithThePermissionAndTheLocationServicesOffAsksToTurnThemOn() = runTest {
        settings.enabled.value = false
        val viewModel = viewModel()
        viewModel.onPermissionChanged(true)
        val events = collectEvents(viewModel)

        viewModel.onWentOnline()

        assertThat(events).containsExactly(LocationAction.TURN_ON_SERVICES)
    }

    @Test
    fun goingOnlineWithEverythingOnAsksForNothing() = runTest {
        val viewModel = viewModel()
        viewModel.onPermissionChanged(true)
        val events = collectEvents(viewModel)

        viewModel.onWentOnline()

        assertThat(events).isEmpty()
    }

    @Test
    fun whenTheLocationServicesGoOffMidDeliveryTheCourierIsPromptedOnceAndAgainOnTheNextEpisode() = runTest {
        delivering()
        val viewModel = viewModel()
        viewModel.onPermissionChanged(true)
        val events = collectEvents(viewModel)

        settings.enabled.value = false
        assertThat(events).containsExactly(LocationAction.TURN_ON_SERVICES)

        settings.enabled.value = true
        settings.enabled.value = false
        assertThat(events).containsExactly(LocationAction.TURN_ON_SERVICES, LocationAction.TURN_ON_SERVICES)
    }

    @Test
    fun thePromptsAreKeptForACollectorThatStartsLate() = runTest {
        settings.enabled.value = false
        val viewModel = viewModel()
        viewModel.onPermissionChanged(true)
        viewModel.onWentOnline()

        val events = collectEvents(viewModel)

        assertThat(events).containsExactly(LocationAction.TURN_ON_SERVICES)
    }
}
