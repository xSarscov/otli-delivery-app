package com.otli.app.ordering.adapters.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.otli.app.core.firebase.await
import com.otli.app.core.result.suspendRunCatching
import com.otli.app.ordering.application.OrderRepository
import com.otli.app.ordering.domain.Actor
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderDraft
import com.otli.app.ordering.domain.OrderStatus
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** `orders/{orderId}` in Firestore. Listener errors close the flow, so collectors must `catch`. */
class FirestoreOrderRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : OrderRepository {

    override suspend fun place(draft: OrderDraft): Result<String> = suspendRunCatching {
        val order = orders().document()
        order.set(OrderDocuments.draftData(draft)).await()
        order.id
    }

    override fun observe(orderId: String): Flow<Order?> = callbackFlow {
        val registration = orders().document(orderId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snapshot?.data?.let { OrderDocuments.orderFrom(snapshot.id, it) })
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeForCustomer(customerId: String): Flow<List<Order>> = observeWhere("customerId", customerId)

    override fun observeForMerchant(merchantId: String): Flow<List<Order>> = observeWhere("merchantId", merchantId)

    override suspend fun transition(orderId: String, to: OrderStatus, actor: Actor, reason: String?): Result<Unit> =
        suspendRunCatching {
            val update = requireNotNull(OrderDocuments.transitionUpdate(to, actor, reason)) { "A rejection needs a reason" }
            orders().document(orderId).update(update).await()
            Unit
        }

    /** Newest first; backed by the `customerId`/`merchantId` + `createdAt` composite indexes. */
    private fun observeWhere(field: String, id: String): Flow<List<Order>> = callbackFlow {
        val registration = orders()
            .whereEqualTo(field, id)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.documents.orEmpty().mapNotNull { OrderDocuments.orderFrom(it.id, it.data.orEmpty()) })
                }
            }
        awaitClose { registration.remove() }
    }

    private fun orders() = firestore.collection(OrderDocuments.ORDERS)
}
