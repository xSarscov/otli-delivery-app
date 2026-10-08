package com.otli.app.admin.adapters.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.otli.app.admin.application.AdminRepository
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import com.otli.app.core.firebase.await
import com.otli.app.core.money.Money
import com.otli.app.core.result.DomainError
import com.otli.app.core.result.DomainException
import com.otli.app.core.result.suspendRunCatching
import com.otli.app.dispatch.adapters.firestore.DispatchDocuments
import com.otli.app.ordering.adapters.firestore.OrderDocuments
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus
import javax.inject.Inject
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The Admin's side of Firestore. The release writes the order and the courier's slot in one
 * transaction, which the rules require to be paired (ADR-7); an account status change writes
 * `users/{uid}` and, for a merchant, its storefront mirror in one batch. Listener errors close the
 * flow, so collectors must `catch`. The rules refuse everything unless the signed-in user is an
 * active Admin, so none of these writes is reachable from another role.
 */
class FirestoreAdminRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : AdminRepository {

    override suspend fun setAccountStatus(uid: String, role: Role, status: AccountStatus): Result<Unit> = suspendRunCatching {
        val batch = firestore.batch()
        batch.update(firestore.collection(AdminDocuments.USERS).document(uid), AdminDocuments.userStatusUpdate(status))
        if (AdminDocuments.hasMirror(role)) {
            batch.update(firestore.collection(AdminDocuments.MERCHANTS).document(uid), AdminDocuments.merchantMirrorUpdate(status))
        }
        batch.commit().await()
        Unit
    }

    override suspend fun setDeliveryFee(fee: Money): Result<Unit> = suspendRunCatching {
        val adminUid = checkNotNull(auth.currentUser?.uid) { "Only a signed-in Admin sets the fee" }
        firestore.collection(AdminDocuments.SETTINGS).document(AdminDocuments.SETTINGS_APP)
            .set(AdminDocuments.feeData(fee, adminUid))
            .await()
        Unit
    }

    override suspend fun releaseClaim(orderId: String): Result<Unit> = suspendRunCatching {
        firestore.runTransaction { transaction ->
            val orderRef = orders().document(orderId)
            val courierId = AdminDocuments.courierToFree(transaction.get(orderRef).data)
                ?: throw DomainException(DomainError.InvalidTransition(OrderStatus.CLAIMED.wire, OrderStatus.READY.wire))
            transaction.update(orderRef, AdminDocuments.releaseOrderUpdate())
            transaction.update(firestore.collection(DispatchDocuments.COURIERS).document(courierId), AdminDocuments.releaseCourierUpdate())
        }.await()
        Unit
    }

    override suspend fun cancelOrder(orderId: String, reason: String): Result<Unit> = suspendRunCatching {
        orders().document(orderId).update(AdminDocuments.cancelUpdate(reason)).await()
        Unit
    }

    override fun observeStuckOrders(): Flow<List<Order>> =
        observeOrders(orders().whereIn("status", ACTIONABLE_STATUSES.map { it.wire }))

    override fun observeManagedAccounts(): Flow<List<UserAccount>> = callbackFlow {
        val registration = firestore.collection(AdminDocuments.USERS)
            .whereIn("role", listOf(Role.MERCHANT, Role.COURIER).map { it.name.lowercase() })
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot.accounts())
                }
            }
        awaitClose { registration.remove() }
    }

    /** The latest [ALL_ORDERS_LIMIT] orders, backed by the automatic `createdAt` index. */
    override fun observeAllOrders(): Flow<List<Order>> =
        observeOrders(orders().orderBy("createdAt", Query.Direction.DESCENDING).limit(ALL_ORDERS_LIMIT))

    private fun observeOrders(query: Query): Flow<List<Order>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            sendOrders(snapshot, error)
        }
        awaitClose { registration.remove() }
    }

    private fun ProducerScope<List<Order>>.sendOrders(snapshot: QuerySnapshot?, error: Exception?) {
        if (error != null) {
            close(error)
        } else {
            trySend(snapshot?.documents.orEmpty().mapNotNull { OrderDocuments.orderFrom(it.id, it.data.orEmpty()) })
        }
    }

    private fun QuerySnapshot?.accounts(): List<UserAccount> =
        this?.documents.orEmpty().mapNotNull { AdminDocuments.accountFrom(it.id, it.data.orEmpty()) }

    private fun orders() = firestore.collection(OrderDocuments.ORDERS)

    companion object {
        /** What Admin can still act on: cancel while no courier holds the order, release once one does. */
        val ACTIONABLE_STATUSES = listOf(
            OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.CLAIMED,
        )

        const val ALL_ORDERS_LIMIT = 50L
    }
}
