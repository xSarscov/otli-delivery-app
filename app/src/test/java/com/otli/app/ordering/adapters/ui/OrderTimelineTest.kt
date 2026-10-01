package com.otli.app.ordering.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.ordering.adapters.ui.StepProgress.CURRENT
import com.otli.app.ordering.adapters.ui.StepProgress.DONE
import com.otli.app.ordering.adapters.ui.StepProgress.UPCOMING
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Test

class OrderTimelineTest {
    private fun steps(status: OrderStatus) = OrderTimeline.of(status).map { it.status to it.progress }

    @Test
    fun aPlacedOrderIsAtTheFirstStepWithEverythingElseAhead() {
        assertThat(steps(OrderStatus.PLACED)).containsExactly(
            OrderStatus.PLACED to CURRENT,
            OrderStatus.ACCEPTED to UPCOMING,
            OrderStatus.PREPARING to UPCOMING,
            OrderStatus.READY to UPCOMING,
            OrderStatus.CLAIMED to UPCOMING,
            OrderStatus.PICKED_UP to UPCOMING,
            OrderStatus.DELIVERED to UPCOMING,
        ).inOrder()
    }

    @Test
    fun stepsBeforeTheCurrentOneAreDone() {
        assertThat(steps(OrderStatus.READY)).containsExactly(
            OrderStatus.PLACED to DONE,
            OrderStatus.ACCEPTED to DONE,
            OrderStatus.PREPARING to DONE,
            OrderStatus.READY to CURRENT,
            OrderStatus.CLAIMED to UPCOMING,
            OrderStatus.PICKED_UP to UPCOMING,
            OrderStatus.DELIVERED to UPCOMING,
        ).inOrder()
    }

    @Test
    fun aDeliveredOrderEndsOnTheLastStep() {
        val delivered = steps(OrderStatus.DELIVERED)

        assertThat(delivered.dropLast(1).map { it.second }.toSet()).containsExactly(DONE)
        assertThat(delivered.last()).isEqualTo(OrderStatus.DELIVERED to CURRENT)
    }

    @Test
    fun aRejectedOrderStopsAfterBeingPlaced() {
        assertThat(steps(OrderStatus.REJECTED)).containsExactly(OrderStatus.PLACED to DONE, OrderStatus.REJECTED to CURRENT).inOrder()
    }

    @Test
    fun aCancelledOrderStopsAfterBeingPlaced() {
        assertThat(steps(OrderStatus.CANCELLED)).containsExactly(OrderStatus.PLACED to DONE, OrderStatus.CANCELLED to CURRENT).inOrder()
    }

    @Test
    fun everyStatusHasExactlyOneCurrentStep() {
        for (status in OrderStatus.entries) {
            assertThat(OrderTimeline.of(status).count { it.progress == CURRENT }).isEqualTo(1)
            assertThat(OrderTimeline.of(status).single { it.progress == CURRENT }.status).isEqualTo(status)
        }
    }
}
