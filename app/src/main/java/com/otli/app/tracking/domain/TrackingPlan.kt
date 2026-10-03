package com.otli.app.tracking.domain

import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.ordering.domain.OrderStatus

/**
 * What the courier's phone should be doing about live tracking (ADR-8): share the location while an
 * online courier serves an order that is `claimed` or `picked_up`, and nothing otherwise. Without the
 * location permission, or with the device location services off, the courier still delivers, but the
 * customer sees no position, so the app says so (the permission first: it is the one the app can ask for
 * directly).
 */
sealed interface TrackingPlan {
    data object Idle : TrackingPlan

    /** Publish the position of [orderId] as [courierId]. */
    data class Share(val orderId: String, val courierId: String) : TrackingPlan

    /** Tracking is due for [orderId] but the location permission is missing. */
    data class NeedsPermission(val orderId: String) : TrackingPlan

    /**
     * Tracking is due and the permission is held, but the device location services are off, so no position
     * is produced. The sharing keeps running ([orderId], [courierId]) and resumes by itself when they come back.
     */
    data class ServicesOff(val orderId: String, val courierId: String) : TrackingPlan

    companion object {
        /** [orderStatus] is the status of the order in the courier's slot, null while unknown. */
        fun of(
            courierId: String,
            courier: CourierAvailability?,
            orderStatus: OrderStatus?,
            permitted: Boolean,
            servicesOn: Boolean,
        ): TrackingPlan {
            val orderId = courier?.activeOrderId ?: return Idle
            if (!courier.isOnline) return Idle
            if (orderStatus != OrderStatus.CLAIMED && orderStatus != OrderStatus.PICKED_UP) return Idle
            return when {
                !permitted -> NeedsPermission(orderId)
                !servicesOn -> ServicesOff(orderId, courierId)
                else -> Share(orderId, courierId)
            }
        }
    }
}
