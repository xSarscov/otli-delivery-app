package com.otli.app.dispatch.domain

/**
 * What `couriers/{uid}` says about a courier: whether they take deliveries and the order they are
 * serving. While [activeOrderId] is set (claimed or picked up) the courier cannot go offline and
 * sees no pool.
 */
data class CourierAvailability(
    val isOnline: Boolean,
    val activeOrderId: String?,
) {
    val isBusy: Boolean get() = activeOrderId != null
}
