package com.otli.app.tracking.domain

import com.google.common.truth.Truth.assertThat
import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Test

class TrackingPlanTest {
    private val working = CourierAvailability(isOnline = true, activeOrderId = "o1")

    private fun plan(
        courier: CourierAvailability? = working,
        status: OrderStatus? = OrderStatus.CLAIMED,
        permitted: Boolean = true,
    ) = TrackingPlan.of("courier-1", courier, status, permitted)

    @Test
    fun anOnlineCourierWithAClaimedOrderSharesTheLocation() {
        assertThat(plan(status = OrderStatus.CLAIMED)).isEqualTo(TrackingPlan.Share("o1", "courier-1"))
    }

    @Test
    fun theSharingContinuesOncePickedUp() {
        assertThat(plan(status = OrderStatus.PICKED_UP)).isEqualTo(TrackingPlan.Share("o1", "courier-1"))
    }

    @Test
    fun withoutThePermissionTheCourierIsToldInsteadOfShared() {
        assertThat(plan(permitted = false)).isEqualTo(TrackingPlan.NeedsPermission("o1"))
        assertThat(plan(status = OrderStatus.PICKED_UP, permitted = false)).isEqualTo(TrackingPlan.NeedsPermission("o1"))
    }

    @Test
    fun theSlotNamesTheOrderThatIsShared() {
        assertThat(plan(courier = CourierAvailability(isOnline = true, activeOrderId = "o2"))).isEqualTo(TrackingPlan.Share("o2", "courier-1"))
    }

    @Test
    fun anOfflineCourierDoesNotShare() {
        assertThat(plan(courier = CourierAvailability(isOnline = false, activeOrderId = "o1"))).isEqualTo(TrackingPlan.Idle)
        assertThat(plan(courier = CourierAvailability(isOnline = false, activeOrderId = "o1"), permitted = false)).isEqualTo(TrackingPlan.Idle)
    }

    @Test
    fun aCourierWithoutAnActiveOrderDoesNotShare() {
        assertThat(plan(courier = CourierAvailability(isOnline = true, activeOrderId = null))).isEqualTo(TrackingPlan.Idle)
        assertThat(plan(courier = null)).isEqualTo(TrackingPlan.Idle)
    }

    @Test
    fun onlyClaimedAndPickedUpOrdersAreShared() {
        for (status in OrderStatus.entries - setOf(OrderStatus.CLAIMED, OrderStatus.PICKED_UP)) {
            assertThat(plan(status = status)).isEqualTo(TrackingPlan.Idle)
        }
        assertThat(plan(status = null)).isEqualTo(TrackingPlan.Idle)
    }
}
