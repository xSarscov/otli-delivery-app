package com.otli.app.ordering.adapters.firestore

import com.google.common.truth.Truth.assertThat
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
import org.junit.Test

class OrderDocumentsTest {
    private val draft = OrderDraft(
        customerId = "customer-1",
        customerName = "Ana Lopez",
        customerPhone = "+50588880201",
        merchantId = "m1",
        merchantName = "Comedor Marta",
        pickup = OrderLocation(12.2667, -86.5667, "Frente al parque"),
        dropoff = OrderLocation(12.27, -86.57, "Casa azul"),
        items = listOf(OrderItem("p1", "Nacatamal", Money(12000), 2), OrderItem("p2", "Fresco", Money(2500), 1)),
        totals = Totals(subtotal = Money(26500), fee = Money(3000), total = Money(29500)),
    )

    private val document: Map<String, Any?> = mapOf(
        "customerId" to "customer-1",
        "customerName" to "Ana Lopez",
        "customerPhone" to "+50588880201",
        "merchantId" to "m1",
        "merchantName" to "Comedor Marta",
        "pickup" to mapOf("lat" to 12.2667, "lng" to -86.5667, "reference" to "Frente al parque"),
        "dropoff" to mapOf("lat" to 12.27, "lng" to -86.57, "reference" to "Casa azul"),
        "items" to listOf(
            mapOf("productId" to "p1", "name" to "Nacatamal", "unitPriceCents" to 12000L, "quantity" to 2L),
            mapOf("productId" to "p2", "name" to "Fresco", "unitPriceCents" to 2500L, "quantity" to 1L),
        ),
        "subtotalCents" to 26500L,
        "deliveryFeeCents" to 3000L,
        "totalCents" to 29500L,
        "paymentMethod" to "cash",
        "status" to "placed",
        "courierId" to null,
        "createdAt" to Timestamp(1_700_000_000L, 0),
    )

    @Test
    fun draftDataCarriesExactlyTheFieldsTheCreateRuleAllows() {
        val data = OrderDocuments.draftData(draft)

        assertThat(data.keys).containsExactly(
            "customerId", "customerName", "customerPhone", "merchantId", "merchantName", "pickup", "dropoff",
            "items", "subtotalCents", "deliveryFeeCents", "totalCents", "paymentMethod", "status", "courierId",
            "createdAt", "updatedAt",
        )
    }

    @Test
    fun draftDataSnapshotsThePlacedStateWithCashAndAnUnassignedCourier() {
        val data = OrderDocuments.draftData(draft)

        assertThat(data["customerId"]).isEqualTo("customer-1")
        assertThat(data["merchantId"]).isEqualTo("m1")
        assertThat(data["paymentMethod"]).isEqualTo("cash")
        assertThat(data["status"]).isEqualTo("placed")
        assertThat(data["courierId"]).isNull()
        assertThat(data["createdAt"]).isInstanceOf(FieldValue::class.java)
        assertThat(data["updatedAt"]).isInstanceOf(FieldValue::class.java)
    }

    @Test
    fun draftDataStoresMoneyAsIntegerCentavosAndPinsAsLatLngReference() {
        val data = OrderDocuments.draftData(draft)

        assertThat(data["subtotalCents"]).isEqualTo(26500L)
        assertThat(data["deliveryFeeCents"]).isEqualTo(3000L)
        assertThat(data["totalCents"]).isEqualTo(29500L)
        assertThat(data["dropoff"]).isEqualTo(mapOf("lat" to 12.27, "lng" to -86.57, "reference" to "Casa azul"))
        assertThat(data["pickup"]).isEqualTo(mapOf("lat" to 12.2667, "lng" to -86.5667, "reference" to "Frente al parque"))
        assertThat(data["items"]).isEqualTo(
            listOf(
                mapOf("productId" to "p1", "name" to "Nacatamal", "unitPriceCents" to 12000L, "quantity" to 2),
                mapOf("productId" to "p2", "name" to "Fresco", "unitPriceCents" to 2500L, "quantity" to 1),
            ),
        )
    }

    @Test
    fun anOrderDocumentMapsToTheDomainOrder() {
        val order = OrderDocuments.orderFrom("o1", document)

        assertThat(order).isEqualTo(
            Order(
                id = "o1",
                customerId = "customer-1",
                customerName = "Ana Lopez",
                customerPhone = "+50588880201",
                merchantId = "m1",
                merchantName = "Comedor Marta",
                pickup = OrderLocation(12.2667, -86.5667, "Frente al parque"),
                dropoff = OrderLocation(12.27, -86.57, "Casa azul"),
                items = draft.items,
                totals = draft.totals,
                status = OrderStatus.PLACED,
                courierId = null,
                rejectReason = null,
                createdAtMillis = 1_700_000_000_000L,
            ),
        )
    }

    @Test
    fun laterStatusCourierAndRejectReasonAreMapped() {
        val rejected = OrderDocuments.orderFrom("o2", document + mapOf("status" to "rejected", "rejectReason" to "Sin ingredientes"))
        val claimed = OrderDocuments.orderFrom("o3", document + mapOf("status" to "claimed", "courierId" to "courier-9"))

        assertThat(rejected?.status).isEqualTo(OrderStatus.REJECTED)
        assertThat(rejected?.rejectReason).isEqualTo("Sin ingredientes")
        assertThat(claimed?.status).isEqualTo(OrderStatus.CLAIMED)
        assertThat(claimed?.courierId).isEqualTo("courier-9")
    }

    @Test
    fun aPendingServerTimestampLeavesCreatedAtAtZeroInsteadOfDroppingTheOrder() {
        val order = OrderDocuments.orderFrom("o1", document + mapOf("createdAt" to null))

        assertThat(order?.createdAtMillis).isEqualTo(0L)
        assertThat(order?.id).isEqualTo("o1")
    }

    @Test
    fun aDocumentWithAnUnknownStatusOrNoItemsOrNoMerchantIsIgnored() {
        assertThat(OrderDocuments.orderFrom("x", document + mapOf("status" to "teleported"))).isNull()
        assertThat(OrderDocuments.orderFrom("x", document + mapOf("items" to emptyList<Any>()))).isNull()
        assertThat(OrderDocuments.orderFrom("x", document - "merchantId")).isNull()
        assertThat(OrderDocuments.orderFrom("x", document - "dropoff")).isNull()
    }

    @Test
    fun anItemWithAZeroQuantityMakesTheDocumentUnreadable() {
        val zeroQuantity = listOf(mapOf("productId" to "p1", "name" to "Nacatamal", "unitPriceCents" to 12000L, "quantity" to 0L))

        assertThat(OrderDocuments.orderFrom("x", document + mapOf("items" to zeroQuantity))).isNull()
    }

    @Test
    fun aNegativeAmountMakesTheDocumentUnreadableButZeroIsAllowed() {
        assertThat(OrderDocuments.orderFrom("x", document + mapOf("deliveryFeeCents" to -1L))).isNull()
        assertThat(OrderDocuments.orderFrom("x", document + mapOf("deliveryFeeCents" to 0L, "totalCents" to 26500L))?.totals?.fee).isEqualTo(Money(0))
    }

    @Test
    fun acceptingOrAdvancingStampsOnlyTheStatusItsOwnTimestampAndUpdatedAt() {
        val accept = OrderDocuments.transitionUpdate(OrderStatus.ACCEPTED, Actor.MERCHANT, null)
        val ready = OrderDocuments.transitionUpdate(OrderStatus.READY, Actor.MERCHANT, null)

        assertThat(accept?.keys).containsExactly("status", "acceptedAt", "updatedAt")
        assertThat(accept?.get("status")).isEqualTo("accepted")
        assertThat(ready?.keys).containsExactly("status", "readyAt", "updatedAt")
        assertThat(ready?.get("status")).isEqualTo("ready")
        assertThat(OrderDocuments.transitionUpdate(OrderStatus.PREPARING, Actor.MERCHANT, null)?.keys)
            .containsExactly("status", "preparingAt", "updatedAt")
    }

    @Test
    fun rejectingCarriesTheTrimmedReasonAndNeedsOne() {
        val update = OrderDocuments.transitionUpdate(OrderStatus.REJECTED, Actor.MERCHANT, "  Sin ingredientes ")

        assertThat(update?.keys).containsExactly("status", "rejectReason", "rejectedAt", "updatedAt")
        assertThat(update?.get("rejectReason")).isEqualTo("Sin ingredientes")
        assertThat(OrderDocuments.transitionUpdate(OrderStatus.REJECTED, Actor.MERCHANT, null)).isNull()
        assertThat(OrderDocuments.transitionUpdate(OrderStatus.REJECTED, Actor.MERCHANT, "   ")).isNull()
    }

    @Test
    fun aCustomerCancellationRecordsWhoCancelled() {
        val update = OrderDocuments.transitionUpdate(OrderStatus.CANCELLED, Actor.CUSTOMER, null)

        assertThat(update?.keys).containsExactly("status", "cancelledBy", "cancelledAt", "updatedAt")
        assertThat(update?.get("status")).isEqualTo("cancelled")
        assertThat(update?.get("cancelledBy")).isEqualTo("customer")
    }
}
