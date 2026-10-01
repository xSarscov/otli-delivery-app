package com.otli.app.dispatch.domain

/**
 * Whether a courier may see the pool of claimable orders. An active order hides the pool entirely
 * (the courier is busy with that delivery); otherwise only an online courier sees it. A courier with
 * no document yet counts as offline.
 */
enum class PoolGate {
    OFFLINE,
    BUSY,
    OPEN,
    ;

    companion object {
        fun of(courier: CourierAvailability?): PoolGate = when {
            courier == null -> OFFLINE
            courier.isBusy -> BUSY
            !courier.isOnline -> OFFLINE
            else -> OPEN
        }
    }
}
