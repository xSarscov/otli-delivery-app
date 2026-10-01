package com.otli.app.dispatch.domain

import com.google.common.truth.Truth.assertThat
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Test

class ClaimPolicyTest {
    private val free = CourierState(isActive = true, isOnline = true, activeOrderId = null)

    private fun decide(
        status: OrderStatus = OrderStatus.READY,
        courierId: String? = null,
        courier: CourierState = free,
    ) = ClaimPolicy.decide(status, courierId, courier)

    @Test
    fun anOnlineActiveCourierWithNoOrderClaimsAReadyUnassignedOrder() {
        assertThat(decide()).isEqualTo(ClaimDecision.Allowed)
    }

    @Test
    fun anOrderThatIsNotReadyYetCannotBeClaimed() {
        for (status in listOf(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.REJECTED, OrderStatus.CANCELLED)) {
            assertThat(decide(status = status)).isEqualTo(ClaimDecision.Denied(ClaimDenial.NOT_READY))
        }
    }

    @Test
    fun anOrderAlreadyWithACourierFailsCleanlyWhateverItsStatus() {
        assertThat(decide(status = OrderStatus.CLAIMED, courierId = "courier-x"))
            .isEqualTo(ClaimDecision.Denied(ClaimDenial.ALREADY_CLAIMED))
        assertThat(decide(status = OrderStatus.PICKED_UP, courierId = "courier-x"))
            .isEqualTo(ClaimDecision.Denied(ClaimDenial.ALREADY_CLAIMED))
        assertThat(decide(status = OrderStatus.READY, courierId = "courier-x"))
            .isEqualTo(ClaimDecision.Denied(ClaimDenial.ALREADY_CLAIMED))
    }

    @Test
    fun theOrderReasonWinsOverTheCourierReason() {
        val busyAndOffline = CourierState(isActive = false, isOnline = false, activeOrderId = "other")
        assertThat(decide(status = OrderStatus.CLAIMED, courierId = "courier-x", courier = busyAndOffline))
            .isEqualTo(ClaimDecision.Denied(ClaimDenial.ALREADY_CLAIMED))
        assertThat(decide(status = OrderStatus.PLACED, courier = busyAndOffline))
            .isEqualTo(ClaimDecision.Denied(ClaimDenial.NOT_READY))
    }

    @Test
    fun aCourierWhoseAccountIsNotActiveCannotClaim() {
        assertThat(decide(courier = free.copy(isActive = false)))
            .isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_NOT_ACTIVE))
    }

    @Test
    fun anOfflineCourierCannotClaim() {
        assertThat(decide(courier = free.copy(isOnline = false)))
            .isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_OFFLINE))
    }

    @Test
    fun aCourierWithAnActiveOrderCannotClaimAnother() {
        assertThat(decide(courier = free.copy(activeOrderId = "order-1")))
            .isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_BUSY))
    }

    @Test
    fun courierReasonsAreCheckedAccountThenAvailabilityThenBusy() {
        assertThat(decide(courier = CourierState(isActive = false, isOnline = false, activeOrderId = "o")))
            .isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_NOT_ACTIVE))
        assertThat(decide(courier = CourierState(isActive = true, isOnline = false, activeOrderId = "o")))
            .isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_OFFLINE))
    }

    @Test
    fun exactlyOneOfAllCombinationsIsAllowed() {
        val allowed = mutableListOf<String>()
        var combinations = 0
        for (status in OrderStatus.entries) {
            for (courierId in listOf(null, "courier-x")) {
                for (active in listOf(true, false)) {
                    for (online in listOf(true, false)) {
                        for (activeOrderId in listOf(null, "order-1")) {
                            combinations++
                            val decision = decide(status, courierId, CourierState(active, online, activeOrderId))
                            if (decision == ClaimDecision.Allowed) {
                                allowed += "$status/$courierId/$active/$online/$activeOrderId"
                            }
                        }
                    }
                }
            }
        }
        assertThat(combinations).isEqualTo(OrderStatus.entries.size * 2 * 2 * 2 * 2)
        assertThat(allowed).containsExactly("READY/null/true/true/null")
    }
}
