package com.otli.app.ordering.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MerchantAdvanceTest {
    @Test
    fun theMerchantWalksAnOrderThroughTheKitchenOneStepAtATime() {
        assertThat(OrderTransitions.merchantAdvance(OrderStatus.PLACED)).isEqualTo(OrderStatus.ACCEPTED)
        assertThat(OrderTransitions.merchantAdvance(OrderStatus.ACCEPTED)).isEqualTo(OrderStatus.PREPARING)
        assertThat(OrderTransitions.merchantAdvance(OrderStatus.PREPARING)).isEqualTo(OrderStatus.READY)
    }

    @Test
    fun theMerchantHasNoStepOnceTheOrderIsReadyOrWithTheCourierOrFinished() {
        for (status in listOf(OrderStatus.READY, OrderStatus.CLAIMED, OrderStatus.PICKED_UP, OrderStatus.DELIVERED, OrderStatus.REJECTED, OrderStatus.CANCELLED)) {
            assertThat(OrderTransitions.merchantAdvance(status)).isNull()
        }
    }
}
