package com.otli.app.ordering.adapters.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.otli.app.core.money.Money
import com.otli.app.ordering.domain.Actor
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderDraft
import com.otli.app.ordering.domain.OrderItem
import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.ordering.domain.Totals

/**
 * Pure translation between `orders/{orderId}` document data and the ordering domain, kept free of
 * snapshot objects so it is JVM-testable. Field names follow the design's data model and the
 * field sets the Firestore rules allow on create and on each transition.
 */
internal object OrderDocuments {
    const val ORDERS = "orders"

    /** The whole create payload; the rules require exactly these fields (ADR-10). */
    fun draftData(draft: OrderDraft): Map<String, Any?> = mapOf(
        "customerId" to draft.customerId,
        "customerName" to draft.customerName,
        "customerPhone" to draft.customerPhone,
        "merchantId" to draft.merchantId,
        "merchantName" to draft.merchantName,
        "pickup" to locationData(draft.pickup),
        "dropoff" to locationData(draft.dropoff),
        "items" to draft.items.map(::itemData),
        "subtotalCents" to draft.totals.subtotal.centavos,
        "deliveryFeeCents" to draft.totals.fee.centavos,
        "totalCents" to draft.totals.total.centavos,
        "paymentMethod" to "cash",
        "status" to OrderStatus.PLACED.wire,
        "courierId" to null,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    /** Returns null for a document missing the parts an order screen needs, or with an unknown status. */
    fun orderFrom(id: String, data: Map<String, Any?>): Order? {
        val status = (data["status"] as? String)?.let(OrderStatus::fromWire) ?: return null
        val items = (data["items"] as? List<*>)?.map { itemFrom(it) ?: return null }?.takeIf { it.isNotEmpty() } ?: return null
        return Order(
            id = id,
            customerId = data["customerId"] as? String ?: return null,
            customerName = data["customerName"] as? String ?: "",
            customerPhone = data["customerPhone"] as? String ?: "",
            merchantId = data["merchantId"] as? String ?: return null,
            merchantName = data["merchantName"] as? String ?: "",
            pickup = locationFrom(data["pickup"]) ?: return null,
            dropoff = locationFrom(data["dropoff"]) ?: return null,
            items = items,
            totals = Totals(
                subtotal = money(data["subtotalCents"]) ?: return null,
                fee = money(data["deliveryFeeCents"]) ?: return null,
                total = money(data["totalCents"]) ?: return null,
            ),
            status = status,
            courierId = data["courierId"] as? String,
            rejectReason = data["rejectReason"] as? String,
            createdAtMillis = (data["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
            cancelReason = data["cancelReason"] as? String,
        )
    }

    /**
     * The update for one transition: the new status, the timestamp that status owns, `updatedAt`,
     * and the extra field some transitions carry. Returns null for a rejection without a reason.
     */
    fun transitionUpdate(to: OrderStatus, actor: Actor, reason: String?): Map<String, Any>? {
        val update = mutableMapOf<String, Any>(
            "status" to to.wire,
            timestampField(to) to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (to == OrderStatus.REJECTED) {
            update["rejectReason"] = reason?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        }
        if (to == OrderStatus.CANCELLED) update["cancelledBy"] = actor.wire
        return update
    }

    private fun timestampField(status: OrderStatus) = when (status) {
        OrderStatus.PLACED -> "createdAt"
        OrderStatus.ACCEPTED -> "acceptedAt"
        OrderStatus.PREPARING -> "preparingAt"
        OrderStatus.READY -> "readyAt"
        OrderStatus.CLAIMED -> "claimedAt"
        OrderStatus.PICKED_UP -> "pickedUpAt"
        OrderStatus.DELIVERED -> "deliveredAt"
        OrderStatus.REJECTED -> "rejectedAt"
        OrderStatus.CANCELLED -> "cancelledAt"
    }

    private fun locationData(location: OrderLocation) =
        mapOf("lat" to location.latitude, "lng" to location.longitude, "reference" to location.reference)

    private fun itemData(item: OrderItem) = mapOf(
        "productId" to item.productId,
        "name" to item.name,
        "unitPriceCents" to item.unitPrice.centavos,
        "quantity" to item.quantity,
    )

    private fun itemFrom(raw: Any?): OrderItem? {
        val map = raw as? Map<*, *> ?: return null
        return OrderItem(
            productId = map["productId"] as? String ?: return null,
            name = map["name"] as? String ?: return null,
            unitPrice = money(map["unitPriceCents"]) ?: return null,
            quantity = (map["quantity"] as? Number)?.toInt()?.takeIf { it >= 1 } ?: return null,
        )
    }

    private fun locationFrom(raw: Any?): OrderLocation? {
        val map = raw as? Map<*, *> ?: return null
        return OrderLocation(
            latitude = (map["lat"] as? Number)?.toDouble() ?: return null,
            longitude = (map["lng"] as? Number)?.toDouble() ?: return null,
            reference = map["reference"] as? String ?: "",
        )
    }

    private fun money(raw: Any?): Money? = (raw as? Number)?.toLong()?.takeIf { it >= 0 }?.let(::Money)
}
