package com.otli.app.tracking.adapters.firestore

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.otli.app.auth.adapters.firestore.FirestoreAuthRepository
import com.otli.app.core.di.OtliFirebase
import com.otli.app.core.firebase.await
import com.otli.app.core.money.Money
import com.otli.app.dispatch.adapters.firestore.FirestoreDispatchRepository
import com.otli.app.dispatch.domain.ClaimDecision
import com.otli.app.ordering.adapters.firestore.FirestoreOrderRepository
import com.otli.app.ordering.adapters.firestore.FirestoreSettingsRepository
import com.otli.app.ordering.domain.Actor
import com.otli.app.ordering.domain.OrderDraft
import com.otli.app.ordering.domain.OrderItem
import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.ordering.domain.Totals
import com.otli.app.tracking.domain.GeoFix
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adapter proof against the emulators (seed data: `npm --prefix backend run seed`, run by `test:android`).
 * The default Firebase instance plays the customer, the merchant and the seeded courier 1 in turn.
 * Each test starts and ends with the courier free and offline, decided from SERVER reads (a snapshot
 * listener answers from the local cache first, which lags behind transactions). Documents under
 * `liveLocations` cannot be deleted by the rules, so a test only publishes to orders it just created.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreLocationRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val auth = OtliFirebase.auth(context)
    private val firestore = OtliFirebase.firestore(context)
    private val orders = FirestoreOrderRepository(firestore)
    private val settings = FirestoreSettingsRepository(firestore)
    private val dispatch = FirestoreDispatchRepository(firestore)
    private val locations = FirestoreLocationRepository(firestore)

    private fun await(block: suspend () -> Unit): Unit = runBlocking { withTimeout(120_000) { block() } }

    private suspend fun signIn(email: String) {
        auth.signOut()
        FirestoreAuthRepository(auth, firestore).login(email, PASSWORD).getOrThrow()
    }

    /** A new order taken to `ready` by the seeded customer and merchant, then claimed by courier 1. */
    private suspend fun claimedOrder(): String {
        signIn(CUSTOMER_EMAIL)
        val fee = settings.deliveryFee().getOrThrow()
        val subtotal = Money(24000)
        val draft = OrderDraft(
            customerId = CUSTOMER_ID,
            customerName = "Ana Lopez",
            customerPhone = "+50588880201",
            merchantId = MERCHANT_1,
            merchantName = "Comedor Doña Marta",
            pickup = OrderLocation(12.2667, -86.5667, "Frente al parque central de Nagarote"),
            dropoff = OrderLocation(12.27, -86.57, "Casa azul"),
            items = listOf(OrderItem("prod-nacatamal", "Nacatamal", Money(12000), 2)),
            totals = Totals(subtotal, fee, subtotal + fee),
        )
        val id = orders.place(draft).getOrThrow()
        signIn(MERCHANT_EMAIL)
        orders.transition(id, OrderStatus.ACCEPTED, Actor.MERCHANT).getOrThrow()
        orders.transition(id, OrderStatus.PREPARING, Actor.MERCHANT).getOrThrow()
        orders.transition(id, OrderStatus.READY, Actor.MERCHANT).getOrThrow()
        signIn(COURIER_EMAIL)
        dispatch.setOnline(COURIER, true).getOrThrow()
        assertThat(dispatch.claim(id, COURIER).getOrThrow()).isEqualTo(ClaimDecision.Allowed)
        return id
    }

    private fun fix(latitude: Double = 12.2656, longitude: Double = -86.5664) =
        GeoFix(latitude, longitude, accuracyMeters = 7f, timestampMillis = System.currentTimeMillis())

    /** The stored position as the server holds it, read through the signed-in client. */
    private suspend fun storedLatitude(orderId: String): Double? =
        firestore.collection("liveLocations").document(orderId).get(Source.SERVER).await().getDouble("lat")

    @Before
    fun startFromAFreeCourier() = await { release(strict = true) }

    @After
    fun freeTheCourier() = runBlocking {
        runCatching { withTimeout(60_000) { release(strict = false) } }
        auth.signOut()
        Unit
    }

    private suspend fun release(strict: Boolean) {
        if (auth.currentUser?.uid != COURIER) signIn(COURIER_EMAIL)
        val active = firestore.collection("couriers").document(COURIER).get(Source.SERVER).await().getString("activeOrderId")
        if (active != null) {
            val status = firestore.collection("orders").document(active).get(Source.SERVER).await().getString("status")
            if (status == OrderStatus.CLAIMED.wire) dispatch.markPickedUp(active, COURIER).also { if (strict) it.getOrThrow() }
            dispatch.markDelivered(active, COURIER).also { if (strict) it.getOrThrow() }
        }
        dispatch.setOnline(COURIER, false).also { if (strict) it.getOrThrow() }
    }

    @Test
    fun theClaimingCourierPublishesAndTheCustomerObservesThePosition() = await {
        val id = claimedOrder()

        locations.publish(id, COURIER, fix(latitude = 12.2656)).getOrThrow()

        assertThat(storedLatitude(id)).isEqualTo(12.2656)
        signIn(CUSTOMER_EMAIL)
        val seen = locations.observe(id).first { it != null }!!
        assertThat(seen.courierId).isEqualTo(COURIER)
        assertThat(seen.latitude).isEqualTo(12.2656)
        assertThat(seen.longitude).isEqualTo(-86.5664)
        assertThat(seen.updatedAtMillis).isGreaterThan(0L)
    }

    @Test
    fun aSecondPublishWithinFiveSecondsIsRefusedByTheRulesAndSurfacedAsAFailure() = await {
        val id = claimedOrder()
        locations.publish(id, COURIER, fix(latitude = 12.2656)).getOrThrow()

        val second = locations.publish(id, COURIER, fix(latitude = 12.2700))

        assertThat(second.isFailure).isTrue()
        assertThat((second.exceptionOrNull() as? FirebaseFirestoreException)?.code).isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
        assertThat(storedLatitude(id)).isEqualTo(12.2656)
    }

    @Test
    fun theMerchantCannotObserveThePosition() = await {
        val id = claimedOrder()
        locations.publish(id, COURIER, fix()).getOrThrow()
        signIn(MERCHANT_EMAIL)

        val outcome = runCatching { locations.observe(id).first() }

        assertThat(outcome.isFailure).isTrue()
    }

    @Test
    fun noPositionIsPublishedAfterTheDelivery() = await {
        val id = claimedOrder()
        locations.publish(id, COURIER, fix()).getOrThrow()
        dispatch.markPickedUp(id, COURIER).getOrThrow()
        dispatch.markDelivered(id, COURIER).getOrThrow()
        delay(PAST_THE_FLOOR_MILLIS)

        val late = locations.publish(id, COURIER, fix(latitude = 12.30))

        assertThat(late.isFailure).isTrue()
        assertThat(storedLatitude(id)).isEqualTo(12.2656)
    }

    private companion object {
        const val PASSWORD = "otli-demo-123"
        const val CUSTOMER_ID = "seed-customer-1"
        const val CUSTOMER_EMAIL = "customer1@otli.test"
        const val MERCHANT_1 = "seed-merchant-1"
        const val MERCHANT_EMAIL = "merchant1@otli.test"
        const val COURIER = "seed-courier-1"
        const val COURIER_EMAIL = "courier1@otli.test"
        const val PAST_THE_FLOOR_MILLIS = 5_500L
    }
}
