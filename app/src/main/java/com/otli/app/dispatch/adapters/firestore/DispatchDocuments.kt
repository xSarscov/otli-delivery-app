package com.otli.app.dispatch.adapters.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.otli.app.dispatch.domain.ClaimDecision
import com.otli.app.dispatch.domain.ClaimDenial
import com.otli.app.dispatch.domain.ClaimPolicy
import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.dispatch.domain.CourierState
import com.otli.app.dispatch.domain.PoolOrder
import com.otli.app.ordering.adapters.firestore.OrderDocuments
import com.otli.app.ordering.domain.OrderStatus

/**
 * Pure translation between the courier-side documents (`couriers/{uid}`, the courier's writes on
 * `orders/{orderId}`) and the dispatch domain, free of snapshot objects so it is JVM-testable. Every
 * update carries exactly the fields the Firestore rules allow for that step (ADR-7).
 */
internal object DispatchDocuments {
    const val COURIERS = "couriers"
    const val USERS = "users"

    /** Null when the document lacks the online flag, i.e. it is not a courier document we can use. */
    fun availabilityFrom(data: Map<String, Any?>): CourierAvailability? = CourierAvailability(
        isOnline = data["isOnline"] as? Boolean ?: return null,
        activeOrderId = data["activeOrderId"] as? String,
    )

    fun onlineUpdate(online: Boolean): Map<String, Any> = mapOf(
        "isOnline" to online,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    /** Null for anything that is not an unclaimed `ready` order the pool can show. */
    fun poolOrderFrom(id: String, data: Map<String, Any?>): PoolOrder? {
        val order = OrderDocuments.orderFrom(id, data) ?: return null
        if (order.status != OrderStatus.READY || order.courierId != null) return null
        return PoolOrder(
            id = id,
            merchantName = order.merchantName,
            pickup = order.pickup,
            dropoff = order.dropoff,
            totals = order.totals,
            readyAtMillis = (data["readyAt"] as? Timestamp)?.toDate()?.time ?: 0L,
        )
    }

    /**
     * The claim pre-conditions as read inside the transaction: the order, the courier document and the
     * `users` document (which holds the account status). A missing document counts as the failing
     * condition it implies; the rules re-check everything at commit.
     */
    fun claimDecision(order: Map<String, Any?>?, courier: Map<String, Any?>?, user: Map<String, Any?>?): ClaimDecision {
        val status = (order?.get("status") as? String)?.let(OrderStatus::fromWire) ?: return ClaimDecision.Denied(ClaimDenial.NOT_READY)
        val availability = courier?.let(::availabilityFrom)
        val state = CourierState(
            isActive = user?.get("role") == "courier" && user["status"] == "active",
            isOnline = availability?.isOnline == true,
            activeOrderId = availability?.activeOrderId,
        )
        return ClaimPolicy.decide(status, order["courierId"] as? String, state)
    }

    /**
     * A courier that loses the race can no longer read the order it just lost (it now belongs to
     * someone else), so a denied read or commit during the claim means "already claimed". Anything
     * else is a real failure and yields null.
     */
    fun decisionForFailure(failure: Throwable): ClaimDecision.Denied? =
        if (failure is FirebaseFirestoreException && failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            ClaimDecision.Denied(ClaimDenial.ALREADY_CLAIMED)
        } else {
            null
        }

    fun claimOrderUpdate(courierId: String): Map<String, Any> = mapOf(
        "status" to OrderStatus.CLAIMED.wire,
        "courierId" to courierId,
        "claimedAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun claimCourierUpdate(orderId: String): Map<String, Any> = mapOf(
        "activeOrderId" to orderId,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun pickUpUpdate(): Map<String, Any> = mapOf(
        "status" to OrderStatus.PICKED_UP.wire,
        "pickedUpAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun deliverOrderUpdate(): Map<String, Any> = mapOf(
        "status" to OrderStatus.DELIVERED.wire,
        "deliveredAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun deliverCourierUpdate(): Map<String, Any?> = mapOf(
        "activeOrderId" to null,
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}
