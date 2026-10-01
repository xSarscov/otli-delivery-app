package com.otli.app.dispatch.application

import com.otli.app.dispatch.domain.ClaimDecision
import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.dispatch.domain.PoolOrder
import kotlinx.coroutines.flow.Flow

/**
 * Port over the courier side of `orders/{orderId}` and `couriers/{uid}`. Observers are live
 * listeners: a failing listener ends its flow with an error, so collectors must handle it
 * (`catch`). The Firestore rules stay the final authority on every write (ADR-7).
 */
interface DispatchRepository {
    /** The `ready`, unclaimed orders, oldest first. Orders claimed by someone else leave the list. */
    fun observePool(): Flow<List<PoolOrder>>

    /**
     * The courier's availability and active order, live; null while `couriers/{courierId}` does not
     * exist. A failing listener ends the flow with an error.
     */
    fun observeCourier(courierId: String): Flow<CourierAvailability?>

    /**
     * Claims [orderId] for [courierId] in one transaction that writes the order and the courier's
     * `activeOrderId` together. A refusal that [com.otli.app.dispatch.domain.ClaimPolicy] explains
     * (someone else won, the courier is busy) is a successful [ClaimDecision.Denied]; a failure is
     * a transport or rules error.
     */
    suspend fun claim(orderId: String, courierId: String): Result<ClaimDecision>

    /** `claimed` to `picked_up`, by the assigned courier only. */
    suspend fun markPickedUp(orderId: String, courierId: String): Result<Unit>

    /** `picked_up` to `delivered` and clears the courier's `activeOrderId` in the same transaction. */
    suspend fun markDelivered(orderId: String, courierId: String): Result<Unit>

    /** Goes online or offline. Going offline is refused while the courier has an active order. */
    suspend fun setOnline(courierId: String, online: Boolean): Result<Unit>
}
