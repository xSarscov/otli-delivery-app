package com.otli.app.admin.application

import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import com.otli.app.core.money.Money
import com.otli.app.ordering.domain.Order
import kotlinx.coroutines.flow.Flow

/**
 * Port over everything only Admin does: platform fee, merchant/courier accounts and the orders no
 * courier can resolve. Observers are live listeners: a failing listener ends its flow with an error,
 * so collectors must handle it (`catch`). The Firestore rules stay the final authority on every
 * write; the use cases in this package only check what the app can explain up front.
 */
interface AdminRepository {
    /**
     * Sets the status of the merchant or courier [uid]. Status lives on `users/{uid}`; a merchant's
     * `merchants/{uid}` mirror is written in the same batch so storefront queries stay consistent.
     */
    suspend fun setAccountStatus(uid: String, role: Role, status: AccountStatus): Result<Unit>

    /** Writes `settings/app.deliveryFeeCents`; existing orders keep the fee they snapshotted. */
    suspend fun setDeliveryFee(fee: Money): Result<Unit>

    /**
     * `claimed` back to `ready`, clearing the order's courier and that courier's `activeOrderId` in
     * the same transaction. The courier is read from the order itself.
     */
    suspend fun releaseClaim(orderId: String): Result<Unit>

    /**
     * Cancels an order, recording [reason] and that Admin did it. An order a courier picked up and then abandoned
     * is cancelled in one transaction with that courier's `activeOrderId`, which is cleared; it is never returned
     * to the pool. A `claimed` order must be released first.
     */
    suspend fun cancelOrder(orderId: String, reason: String): Result<Unit>

    /** The orders Admin can still act on: waiting in the kitchen, ready for a courier, claimed or picked up. */
    fun observeStuckOrders(): Flow<List<Order>>

    /** Every merchant and courier account, whatever its status; pending ones are the approval queue. */
    fun observeManagedAccounts(): Flow<List<UserAccount>>

    /** The latest orders of every merchant and customer, for support. */
    fun observeAllOrders(): Flow<List<Order>>
}
