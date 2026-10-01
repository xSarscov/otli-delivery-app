package com.otli.app.dispatch.domain

import com.otli.app.ordering.domain.OrderStatus

/** Why a claim attempt was turned down. */
enum class ClaimDenial { NOT_READY, ALREADY_CLAIMED, COURIER_BUSY, COURIER_OFFLINE, COURIER_NOT_ACTIVE }

sealed interface ClaimDecision {
    data object Allowed : ClaimDecision

    data class Denied(val reason: ClaimDenial) : ClaimDecision
}

/** What the claim needs to know about the courier, read from `users/{uid}` and `couriers/{uid}`. */
data class CourierState(
    val isActive: Boolean,
    val isOnline: Boolean,
    /** The order the courier is serving (claimed or picked up), or null when free. */
    val activeOrderId: String?,
)

/**
 * The pre-conditions of the race-safe claim (ADR-7), read inside the claim transaction. The Firestore
 * rules re-check them at commit and are the real authority; this lets the app explain a refusal.
 *
 * An order that already has a courier is reported as [ClaimDenial.ALREADY_CLAIMED] whatever its
 * status; order reasons come before courier reasons, and courier reasons run account, availability, busy.
 */
object ClaimPolicy {
    fun decide(orderStatus: OrderStatus, orderCourierId: String?, courier: CourierState): ClaimDecision {
        val denial = when {
            orderCourierId != null -> ClaimDenial.ALREADY_CLAIMED
            orderStatus != OrderStatus.READY -> ClaimDenial.NOT_READY
            !courier.isActive -> ClaimDenial.COURIER_NOT_ACTIVE
            !courier.isOnline -> ClaimDenial.COURIER_OFFLINE
            courier.activeOrderId != null -> ClaimDenial.COURIER_BUSY
            else -> return ClaimDecision.Allowed
        }
        return ClaimDecision.Denied(denial)
    }
}
