package com.otli.app.dispatch.adapters.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.otli.app.core.firebase.await
import com.otli.app.core.result.suspendRunCatching
import com.otli.app.dispatch.application.DispatchRepository
import com.otli.app.dispatch.domain.ClaimDecision
import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.dispatch.domain.PoolOrder
import com.otli.app.ordering.adapters.firestore.OrderDocuments
import com.otli.app.ordering.domain.OrderStatus
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * `couriers/{uid}` and the courier's writes on `orders/{orderId}` (ADR-7). The claim and the delivery
 * write the order and the courier's slot in one transaction, which the rules require to be paired.
 * Listener errors close the flow, so collectors must `catch`. The identity of every write is the
 * signed-in user: the `courierId` arguments only choose the courier document, and the rules refuse
 * anyone but that courier.
 */
class FirestoreDispatchRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : DispatchRepository {

    /** Oldest first; backed by the `status` + `readyAt` composite index. */
    override fun observePool(): Flow<List<PoolOrder>> = callbackFlow {
        val registration = orders()
            .whereEqualTo("status", OrderStatus.READY.wire)
            .orderBy("readyAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.documents.orEmpty().mapNotNull { DispatchDocuments.poolOrderFrom(it.id, it.data.orEmpty()) })
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeCourier(courierId: String): Flow<CourierAvailability?> = callbackFlow {
        val registration = couriers().document(courierId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snapshot?.data?.let(DispatchDocuments::availabilityFrom))
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun claim(orderId: String, courierId: String): Result<ClaimDecision> =
        try {
            Result.success(claimInTransaction(orderId, courierId))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // The loser of a race cannot read the order any more (it belongs to someone else): a denial, not an error.
            DispatchDocuments.decisionForFailure(failure)?.let { Result.success(it) } ?: Result.failure(failure)
        }

    override suspend fun markPickedUp(orderId: String, courierId: String): Result<Unit> = suspendRunCatching {
        orders().document(orderId).update(DispatchDocuments.pickUpUpdate()).await()
        Unit
    }

    override suspend fun markDelivered(orderId: String, courierId: String): Result<Unit> = suspendRunCatching {
        firestore.runTransaction { transaction ->
            transaction.update(orders().document(orderId), DispatchDocuments.deliverOrderUpdate())
            transaction.update(couriers().document(courierId), DispatchDocuments.deliverCourierUpdate())
        }.await()
        Unit
    }

    override suspend fun setOnline(courierId: String, online: Boolean): Result<Unit> = suspendRunCatching {
        couriers().document(courierId).update(DispatchDocuments.onlineUpdate(online)).await()
        Unit
    }

    /**
     * Reads the order, the courier and the account, applies [com.otli.app.dispatch.domain.ClaimPolicy]
     * and, when allowed, writes both halves of the claim. If another courier commits first, the SDK
     * re-runs the reads and the order read is then denied, which [claim] reports as already claimed.
     */
    private suspend fun claimInTransaction(orderId: String, courierId: String): ClaimDecision =
        firestore.runTransaction { transaction ->
            val orderRef = orders().document(orderId)
            val courierRef = couriers().document(courierId)
            val order = transaction.get(orderRef)
            val courier = transaction.get(courierRef)
            val user = transaction.get(firestore.collection(DispatchDocuments.USERS).document(courierId))
            val decision = DispatchDocuments.claimDecision(order.data, courier.data, user.data)
            if (decision == ClaimDecision.Allowed) {
                transaction.update(orderRef, DispatchDocuments.claimOrderUpdate(courierId))
                transaction.update(courierRef, DispatchDocuments.claimCourierUpdate(orderId))
            }
            decision
        }.await()

    private fun orders() = firestore.collection(OrderDocuments.ORDERS)

    private fun couriers() = firestore.collection(DispatchDocuments.COURIERS)
}
