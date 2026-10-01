package com.otli.app.dispatch.adapters.firestore

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.otli.app.core.money.Money
import com.otli.app.dispatch.domain.ClaimDecision
import com.otli.app.dispatch.domain.ClaimDenial
import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.dispatch.domain.PoolOrder
import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.Totals
import org.junit.Test

class DispatchDocumentsTest {
    private val readyOrder: Map<String, Any?> = mapOf(
        "customerId" to "customer-1",
        "customerName" to "Ana Lopez",
        "customerPhone" to "+50588880201",
        "merchantId" to "m1",
        "merchantName" to "Comedor Marta",
        "pickup" to mapOf("lat" to 12.2667, "lng" to -86.5667, "reference" to "Frente al parque"),
        "dropoff" to mapOf("lat" to 12.27, "lng" to -86.57, "reference" to "Casa azul"),
        "items" to listOf(mapOf("productId" to "p1", "name" to "Nacatamal", "unitPriceCents" to 12000L, "quantity" to 2L)),
        "subtotalCents" to 24000L,
        "deliveryFeeCents" to 3000L,
        "totalCents" to 27000L,
        "paymentMethod" to "cash",
        "status" to "ready",
        "courierId" to null,
        "createdAt" to Timestamp(1_700_000_000L, 0),
        "readyAt" to Timestamp(1_700_000_600L, 0),
    )

    private val onlineCourier: Map<String, Any?> = mapOf("isOnline" to true, "activeOrderId" to null)
    private val activeCourierUser: Map<String, Any?> = mapOf("role" to "courier", "status" to "active")

    // --- the courier document ---

    @Test
    fun anOnlineFreeCourierIsReadBack() {
        val availability = DispatchDocuments.availabilityFrom(mapOf("isOnline" to true, "activeOrderId" to null))

        assertThat(availability).isEqualTo(CourierAvailability(isOnline = true, activeOrderId = null))
    }

    @Test
    fun anOfflineCourierWithAnActiveOrderIsReadBack() {
        val availability = DispatchDocuments.availabilityFrom(mapOf("isOnline" to false, "activeOrderId" to "o7"))

        assertThat(availability).isEqualTo(CourierAvailability(isOnline = false, activeOrderId = "o7"))
    }

    @Test
    fun aCourierDocumentWithoutAnOnlineFlagIsNotUsable() {
        assertThat(DispatchDocuments.availabilityFrom(mapOf("activeOrderId" to null))).isNull()
        assertThat(DispatchDocuments.availabilityFrom(mapOf("isOnline" to "yes", "activeOrderId" to null))).isNull()
    }

    @Test
    fun theAvailabilityUpdateCarriesExactlyTheFieldsTheRuleAllows() {
        val update = DispatchDocuments.onlineUpdate(true)

        assertThat(update.keys).containsExactly("isOnline", "updatedAt")
        assertThat(update["isOnline"]).isEqualTo(true)
        assertThat(update["updatedAt"]).isEqualTo(FieldValue.serverTimestamp())
        assertThat(DispatchDocuments.onlineUpdate(false)["isOnline"]).isEqualTo(false)
    }

    // --- the pool ---

    @Test
    fun aReadyOrderBecomesAPoolOrderWithItsReadyTimeAndPins() {
        val pool = DispatchDocuments.poolOrderFrom("o1", readyOrder)

        assertThat(pool).isEqualTo(
            PoolOrder(
                id = "o1",
                merchantName = "Comedor Marta",
                pickup = OrderLocation(12.2667, -86.5667, "Frente al parque"),
                dropoff = OrderLocation(12.27, -86.57, "Casa azul"),
                totals = Totals(subtotal = Money(24000), fee = Money(3000), total = Money(27000)),
                readyAtMillis = 1_700_000_600_000L,
            ),
        )
    }

    @Test
    fun aReadyOrderWhoseTimestampIsStillPendingIsListedAsJustReady() {
        val pool = DispatchDocuments.poolOrderFrom("o1", readyOrder - "readyAt")

        assertThat(pool?.readyAtMillis).isEqualTo(0L)
    }

    @Test
    fun anOrderThatIsNotReadyOrAlreadyHasACourierIsNotInThePool() {
        assertThat(DispatchDocuments.poolOrderFrom("o1", readyOrder + ("status" to "claimed"))).isNull()
        assertThat(DispatchDocuments.poolOrderFrom("o1", readyOrder + ("courierId" to "courier-2"))).isNull()
        assertThat(DispatchDocuments.poolOrderFrom("o1", readyOrder - "pickup")).isNull()
    }

    // --- the claim ---

    @Test
    fun theOrderHalfOfTheClaimCarriesExactlyTheFieldsTheRuleAllows() {
        val update = DispatchDocuments.claimOrderUpdate("courier-1")

        assertThat(update.keys).containsExactly("status", "courierId", "claimedAt", "updatedAt")
        assertThat(update["status"]).isEqualTo("claimed")
        assertThat(update["courierId"]).isEqualTo("courier-1")
        assertThat(update["claimedAt"]).isEqualTo(FieldValue.serverTimestamp())
        assertThat(update["updatedAt"]).isEqualTo(FieldValue.serverTimestamp())
    }

    @Test
    fun theCourierHalfOfTheClaimFillsTheSlotWithTheOrder() {
        val update = DispatchDocuments.claimCourierUpdate("o9")

        assertThat(update.keys).containsExactly("activeOrderId", "updatedAt")
        assertThat(update["activeOrderId"]).isEqualTo("o9")
        assertThat(update["updatedAt"]).isEqualTo(FieldValue.serverTimestamp())
    }

    @Test
    fun aFreeOnlineActiveCourierMayClaimAReadyUnclaimedOrder() {
        val decision = DispatchDocuments.claimDecision(readyOrder, onlineCourier, activeCourierUser)

        assertThat(decision).isEqualTo(ClaimDecision.Allowed)
    }

    @Test
    fun anOrderThatNamesACourierIsAlreadyClaimed() {
        val decision = DispatchDocuments.claimDecision(readyOrder + ("courierId" to "courier-2"), onlineCourier, activeCourierUser)

        assertThat(decision).isEqualTo(ClaimDecision.Denied(ClaimDenial.ALREADY_CLAIMED))
    }

    @Test
    fun anOrderThatIsNotReadyOrDoesNotExistCannotBeClaimed() {
        val accepted = DispatchDocuments.claimDecision(readyOrder + ("status" to "accepted"), onlineCourier, activeCourierUser)
        val missing = DispatchDocuments.claimDecision(null, onlineCourier, activeCourierUser)
        val unknownStatus = DispatchDocuments.claimDecision(readyOrder + ("status" to "teleported"), onlineCourier, activeCourierUser)

        assertThat(accepted).isEqualTo(ClaimDecision.Denied(ClaimDenial.NOT_READY))
        assertThat(missing).isEqualTo(ClaimDecision.Denied(ClaimDenial.NOT_READY))
        assertThat(unknownStatus).isEqualTo(ClaimDecision.Denied(ClaimDenial.NOT_READY))
    }

    @Test
    fun anOfflineCourierCannotClaim() {
        val offline = DispatchDocuments.claimDecision(readyOrder, mapOf("isOnline" to false, "activeOrderId" to null), activeCourierUser)
        val noCourierDocument = DispatchDocuments.claimDecision(readyOrder, null, activeCourierUser)

        assertThat(offline).isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_OFFLINE))
        assertThat(noCourierDocument).isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_OFFLINE))
    }

    @Test
    fun aCourierWithAnActiveOrderIsBusy() {
        val busy = DispatchDocuments.claimDecision(readyOrder, mapOf("isOnline" to true, "activeOrderId" to "o7"), activeCourierUser)

        assertThat(busy).isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_BUSY))
    }

    @Test
    fun onlyAnActiveCourierAccountMayClaim() {
        val pending = DispatchDocuments.claimDecision(readyOrder, onlineCourier, mapOf("role" to "courier", "status" to "pending"))
        val suspended = DispatchDocuments.claimDecision(readyOrder, onlineCourier, mapOf("role" to "courier", "status" to "suspended"))
        val merchant = DispatchDocuments.claimDecision(readyOrder, onlineCourier, mapOf("role" to "merchant", "status" to "active"))
        val noProfile = DispatchDocuments.claimDecision(readyOrder, onlineCourier, null)

        assertThat(pending).isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_NOT_ACTIVE))
        assertThat(suspended).isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_NOT_ACTIVE))
        assertThat(merchant).isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_NOT_ACTIVE))
        assertThat(noProfile).isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_NOT_ACTIVE))
    }

    // --- pickup and delivery ---

    @Test
    fun thePickUpUpdateCarriesExactlyTheFieldsTheRuleAllows() {
        val update = DispatchDocuments.pickUpUpdate()

        assertThat(update.keys).containsExactly("status", "pickedUpAt", "updatedAt")
        assertThat(update["status"]).isEqualTo("picked_up")
        assertThat(update["pickedUpAt"]).isEqualTo(FieldValue.serverTimestamp())
    }

    @Test
    fun deliveringUpdatesTheOrderAndFreesTheCouriersSlot() {
        val order = DispatchDocuments.deliverOrderUpdate()
        val courier = DispatchDocuments.deliverCourierUpdate()

        assertThat(order.keys).containsExactly("status", "deliveredAt", "updatedAt")
        assertThat(order["status"]).isEqualTo("delivered")
        assertThat(order["deliveredAt"]).isEqualTo(FieldValue.serverTimestamp())
        assertThat(courier.keys).containsExactly("activeOrderId", "updatedAt")
        assertThat(courier["activeOrderId"]).isNull()
        assertThat(courier["updatedAt"]).isEqualTo(FieldValue.serverTimestamp())
    }
}
