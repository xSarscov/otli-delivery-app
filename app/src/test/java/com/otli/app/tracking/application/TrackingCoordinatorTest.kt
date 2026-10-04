package com.otli.app.tracking.application

import com.google.common.truth.Truth.assertThat
import com.otli.app.dispatch.application.FakeDispatchRepository
import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.FakeSignedInAuth
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.tracking.domain.TrackingPlan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Records what the coordinator asked of the service, in order. */
private class RecordingController : TrackingController {
    val events = mutableListOf<String>()

    override fun start(orderId: String, courierId: String) {
        events += "start:$orderId:$courierId"
    }

    override fun stop() {
        events += "stop"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingCoordinatorTest {
    private val dispatch = FakeDispatchRepository()
    private val orders = FakeOrderRepository()
    private val auth = FakeSignedInAuth("courier-1")
    private val controller = RecordingController()
    private val permitted = MutableStateFlow(true)
    private val servicesOn = MutableStateFlow(true)
    private val coordinator = TrackingCoordinator(dispatch, orders, auth, controller)

    private fun order(id: String, status: OrderStatus) = anOrder(id, status, courierId = "courier-1")

    private fun runCoordinator(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher()) {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { coordinator.run(permitted, servicesOn) }
        block()
        job.cancel()
    }

    // --- start and stop on the courier's active order ---

    @Test
    fun itStartsTheServiceWhenTheActiveOrderBecomesNonNull() = runCoordinator {
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = null)
        assertThat(controller.events).containsExactly("stop")

        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        assertThat(controller.events).containsExactly("stop", "start:o1:courier-1").inOrder()
    }

    @Test
    fun itStopsTheServiceWhenTheActiveOrderClears() = runCoordinator {
        orders.orders.value = listOf(order("o1", OrderStatus.PICKED_UP))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        orders.orders.value = listOf(order("o1", OrderStatus.DELIVERED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = null)

        assertThat(controller.events.last()).isEqualTo("stop")
        assertThat(controller.events).contains("start:o1:courier-1")
    }

    @Test
    fun itStopsTheServiceWhenTheOrderIsDeliveredEvenBeforeTheSlotClears() = runCoordinator {
        orders.orders.value = listOf(order("o1", OrderStatus.PICKED_UP))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        orders.orders.value = listOf(order("o1", OrderStatus.DELIVERED))

        assertThat(controller.events).containsExactly("stop", "start:o1:courier-1", "stop").inOrder()
    }

    @Test
    fun movingFromClaimedToPickedUpKeepsTheSameServiceRunning() = runCoordinator {
        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        orders.orders.value = listOf(order("o1", OrderStatus.PICKED_UP))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        assertThat(controller.events).containsExactly("stop", "start:o1:courier-1").inOrder()
    }

    @Test
    fun anUnchangedCourierDocumentDoesNotRestartTheOrderListener() = runCoordinator {
        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")
        assertThat(orders.observeStarts).isEqualTo(1)

        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        assertThat(orders.observeStarts).isEqualTo(1)
    }

    @Test
    fun aDifferentActiveOrderRestartsTheServiceForIt() = runCoordinator {
        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED), order("o2", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o2")

        assertThat(controller.events).containsExactly("stop", "start:o1:courier-1", "start:o2:courier-1").inOrder()
    }

    @Test
    fun itDoesNotStartWhileTheCourierIsOfflineAndStartsOnceOnline() = runCoordinator {
        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = false, activeOrderId = "o1")
        assertThat(controller.events).containsExactly("stop")

        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        assertThat(controller.events).containsExactly("stop", "start:o1:courier-1").inOrder()
    }

    @Test
    fun itDoesNotStartForAnOrderThatWasNotClaimed() = runCoordinator {
        orders.orders.value = listOf(order("o1", OrderStatus.READY))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        assertThat(controller.events).containsExactly("stop")
    }

    // --- the location permission ---

    @Test
    fun withoutThePermissionItDoesNotStartAndStartsWhenItIsGranted() = runCoordinator {
        permitted.value = false
        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")
        assertThat(controller.events.filter { it.startsWith("start") }).isEmpty()

        permitted.value = true

        assertThat(controller.events.filter { it.startsWith("start") }).containsExactly("start:o1:courier-1")
        assertThat(controller.events.last()).isEqualTo("start:o1:courier-1")
    }

    @Test
    fun revokingThePermissionStopsTheService() = runCoordinator {
        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        permitted.value = false

        assertThat(controller.events).containsExactly("stop", "start:o1:courier-1", "stop").inOrder()
    }

    // --- the device location switch ---

    @Test
    fun withTheLocationServicesOffTheServiceStaysUpSoPublishingResumesWhenTheyComeBack() = runCoordinator {
        servicesOn.value = false
        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        assertThat(controller.events.last()).isEqualTo("start:o1:courier-1")

        servicesOn.value = true

        assertThat(controller.events).containsExactly("stop", "start:o1:courier-1", "start:o1:courier-1").inOrder()
    }

    @Test
    fun turningTheLocationServicesOffMidDeliveryDoesNotStopTheService() = runCoordinator {
        orders.orders.value = listOf(order("o1", OrderStatus.PICKED_UP))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        servicesOn.value = false

        assertThat(controller.events.drop(1)).containsExactly("start:o1:courier-1", "start:o1:courier-1").inOrder()
    }

    @Test
    fun theMissingPermissionIsReportedBeforeTheLocationServices() = runTest(UnconfinedTestDispatcher()) {
        permitted.value = false
        servicesOn.value = false
        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")
        val plans = mutableListOf<TrackingPlan>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { coordinator.plans(permitted, servicesOn).toList(plans) }

        permitted.value = true

        assertThat(plans).containsExactly(TrackingPlan.NeedsPermission("o1"), TrackingPlan.ServicesOff("o1", "courier-1")).inOrder()
        job.cancel()
    }

    @Test
    fun theLocationServicesGoingOffAndOnAreReflectedInThePlans() = runTest(UnconfinedTestDispatcher()) {
        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")
        val plans = mutableListOf<TrackingPlan>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { coordinator.plans(permitted, servicesOn).toList(plans) }

        servicesOn.value = false
        servicesOn.value = true

        assertThat(plans).containsExactly(
            TrackingPlan.Share("o1", "courier-1"),
            TrackingPlan.ServicesOff("o1", "courier-1"),
            TrackingPlan.Share("o1", "courier-1"),
        ).inOrder()
        job.cancel()
    }

    // --- the session and failures ---

    @Test
    fun signingOutStopsTheService() = runCoordinator {
        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        auth.authState.value = null

        assertThat(controller.events).containsExactly("stop", "start:o1:courier-1", "stop").inOrder()
    }

    @Test
    fun aFailingListenerStopsTheServiceInsteadOfCrashing() = runCoordinator {
        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        orders.listenerError = IllegalStateException("permission denied")
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o2")

        assertThat(controller.events).containsExactly("stop", "start:o1:courier-1", "stop").inOrder()
    }

    // --- the plan the screen shows ---

    @Test
    fun thePlansTellTheScreenWhatIsHappening() = runTest(UnconfinedTestDispatcher()) {
        permitted.value = false
        orders.orders.value = listOf(order("o1", OrderStatus.CLAIMED))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")
        val plans = mutableListOf<TrackingPlan>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { coordinator.plans(permitted, servicesOn).toList(plans) }

        permitted.value = true
        orders.orders.value = listOf(order("o1", OrderStatus.DELIVERED))

        assertThat(plans).containsExactly(TrackingPlan.NeedsPermission("o1"), TrackingPlan.Share("o1", "courier-1"), TrackingPlan.Idle).inOrder()
        job.cancel()
    }
}
