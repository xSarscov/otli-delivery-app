package com.otli.app.admin.application

import com.otli.app.core.money.Money
import com.otli.app.ordering.domain.Actor
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.ordering.domain.OrderTransitions
import javax.inject.Inject

/** Sets the flat city delivery fee. Only a positive amount makes sense (the rules require it too). */
class UpdateFee @Inject constructor(private val admin: AdminRepository) {
    suspend operator fun invoke(fee: Money): AdminActionResult =
        if (fee.centavos <= 0) rejected(AdminRejection.FEE_NOT_POSITIVE) else admin.setDeliveryFee(fee).toActionResult()
}

/** Puts a claimed order back in the pool, freeing its courier. Only a `claimed` order can be released. */
class ReleaseClaim @Inject constructor(private val admin: AdminRepository) {
    suspend operator fun invoke(order: Order): AdminActionResult =
        if (!OrderTransitions.isAllowed(order.status, OrderStatus.READY, Actor.ADMIN)) {
            rejected(AdminRejection.ORDER_NOT_RELEASABLE)
        } else {
            admin.releaseClaim(order.id).toActionResult()
        }
}

/**
 * Cancels an order before any courier holds it, with a reason the customer will see. A claimed order
 * has to be released first: [OrderTransitions] is the single source of what Admin may cancel.
 */
class CancelOrder @Inject constructor(private val admin: AdminRepository) {
    suspend operator fun invoke(order: Order, reason: String): AdminActionResult {
        val trimmed = reason.trim()
        return when {
            !OrderTransitions.isAllowed(order.status, OrderStatus.CANCELLED, Actor.ADMIN) -> rejected(AdminRejection.ORDER_NOT_CANCELLABLE)
            trimmed.isEmpty() -> rejected(AdminRejection.REASON_REQUIRED)
            trimmed.length > MAX_REASON_LENGTH -> rejected(AdminRejection.REASON_TOO_LONG)
            else -> admin.cancelOrder(order.id, trimmed).toActionResult()
        }
    }

    companion object {
        /** Matches the `cancelReason` bound in the Admin cancel rule. */
        const val MAX_REASON_LENGTH = 200
    }
}
