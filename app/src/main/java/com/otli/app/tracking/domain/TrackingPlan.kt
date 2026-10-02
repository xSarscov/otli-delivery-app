package com.otli.app.tracking.domain

import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.ordering.domain.OrderStatus

/**
 * What the courier's phone should be doing about live tracking (ADR-8): share the location while an
 * online courier serves an order that is `claimed` or `picked_up`, and nothing otherwise. Without the
 * location permission the courier still delivers, but the customer sees no position, so the app says so.
 */
sealed interface TrackingPlan {
    data object Idle : TrackingPlan

    /** Publish the position of [orderId] as [courierId]. */
    data class Share(val orderId: String, val courierId: String) : TrackingPlan

    /** Tracking is due for [orderId] but the location permission is missing. */
    data class NeedsPermission(val orderId: String) : TrackingPlan

    companion object {
        /** [orderStatus] is the status of the order in the courier's slot, null while unknown. */
        fun of(courierId: String, courier: CourierAvailability?, orderStatus: OrderStatus?, permitted: Boolean): TrackingPlan {
            val orderId = courier?.activeOrderId ?: return Idle
            if (!courier.isOnline) return Idle
            if (orderStatus != OrderStatus.CLAIMED && orderStatus != OrderStatus.PICKED_UP) return Idle
            return if (permitted) Share(orderId, courierId) else NeedsPermission(orderId)
        }
    }
}
