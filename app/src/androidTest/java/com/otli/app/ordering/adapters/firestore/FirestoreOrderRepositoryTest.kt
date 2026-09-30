package com.otli.app.ordering.adapters.firestore

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.adapters.firestore.FirestoreAuthRepository
import com.otli.app.core.di.OtliFirebase
import com.otli.app.core.money.Money
import com.otli.app.ordering.domain.Actor
import com.otli.app.ordering.domain.OrderDraft
import com.otli.app.ordering.domain.OrderItem
import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.ordering.domain.Totals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adapter proof against the emulators. Needs the seed data (`npm --prefix backend run seed`, run by
 * `test:android`): the seeded customer places orders with the seeded merchant, who then works them.
 * Orders cannot be deleted by the rules, so each run leaves its orders in the emulator.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreOrderRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val auth = OtliFirebase.auth(context)
    private val firestore = OtliFirebase.firestore(context)
    private val orders = FirestoreOrderRepository(firestore)
    private val settings = FirestoreSettingsRepository(firestore)

    // Returns Unit so every `@Test fun x() = await { ... }` compiles to a void JUnit method.
    private fun await(block: suspend () -> Unit): Unit = runBlocking { withTimeout(60_000) { block() } }

    private suspend fun signIn(email: String) {
        auth.signOut()
        FirestoreAuthRepository(auth, firestore).login(email, PASSWORD).getOrThrow()
    }

    private suspend fun placeAsCustomer(reference: String = "Casa azul"): String {
        signIn(CUSTOMER_EMAIL)
        val fee = settings.deliveryFee().getOrThrow()
        val items = listOf(OrderItem("prod-nacatamal", "Nacatamal", Money(12000), 2))
        val subtotal = Money(24000)
        val draft = OrderDraft(
            customerId = CUSTOMER_ID,
            customerName = "Ana Lopez",
            customerPhone = "+50588880201",
            merchantId = MERCHANT_1,
            merchantName = "Comedor Doña Marta",
            pickup = OrderLocation(12.2667, -86.5667, "Frente al parque central de Nagarote"),
            dropoff = OrderLocation(12.27, -86.57, reference),
            items = items,
            totals = Totals(subtotal, fee, subtotal + fee),
        )
        return orders.place(draft).getOrThrow()
    }

    @After
    fun signOut() {
        auth.signOut()
    }

    @Test
    fun theDeliveryFeeIsTheSeededSettingsValue() = await {
        signIn(CUSTOMER_EMAIL)

        assertThat(settings.deliveryFee().getOrThrow()).isEqualTo(Money(3000))
    }

    @Test
    fun aPlacedOrderIsObservedBackWithItsSnapshotAndThePlacedStatus() = await {
        val id = placeAsCustomer(reference = "Casa azul frente a la pulperia")

        val order = orders.observe(id).first { it != null }!!

        assertThat(order.status).isEqualTo(OrderStatus.PLACED)
        assertThat(order.customerId).isEqualTo(CUSTOMER_ID)
        assertThat(order.merchantId).isEqualTo(MERCHANT_1)
        assertThat(order.dropoff.reference).isEqualTo("Casa azul frente a la pulperia")
        assertThat(order.items.map { it.name to it.quantity }).containsExactly("Nacatamal" to 2)
        assertThat(order.totals.total).isEqualTo(Money(27000))
    }

    @Test
    fun theCustomerListAndTheMerchantListBothHoldThePlacedOrder() = await {
        val id = placeAsCustomer()

        val forCustomer = orders.observeForCustomer(CUSTOMER_ID).first { list -> list.any { it.id == id } }
        assertThat(forCustomer.map { it.customerId }.distinct()).containsExactly(CUSTOMER_ID)

        signIn(MERCHANT_EMAIL)
        val forMerchant = orders.observeForMerchant(MERCHANT_1).first { list -> list.any { it.id == id } }
        assertThat(forMerchant.map { it.merchantId }.distinct()).containsExactly(MERCHANT_1)
    }

    @Test
    fun aMerchantAcceptingTheOrderIsDeliveredToTheCustomersListener() = await {
        val id = placeAsCustomer()
        signIn(MERCHANT_EMAIL)
        orders.transition(id, OrderStatus.ACCEPTED, Actor.MERCHANT).getOrThrow()
        orders.transition(id, OrderStatus.PREPARING, Actor.MERCHANT).getOrThrow()

        signIn(CUSTOMER_EMAIL)
        val order = orders.observe(id).first { it?.status == OrderStatus.PREPARING }

        assertThat(order?.status).isEqualTo(OrderStatus.PREPARING)
    }

    @Test
    fun rejectingStoresTheReasonAndTheCustomerReadsIt() = await {
        val id = placeAsCustomer()
        signIn(MERCHANT_EMAIL)
        orders.transition(id, OrderStatus.REJECTED, Actor.MERCHANT, "Sin ingredientes hoy").getOrThrow()

        signIn(CUSTOMER_EMAIL)
        val order = orders.observe(id).first { it?.status == OrderStatus.REJECTED }

        assertThat(order?.rejectReason).isEqualTo("Sin ingredientes hoy")
    }

    @Test
    fun rejectingWithoutAReasonFailsBeforeAnythingIsWritten() = await {
        val id = placeAsCustomer()
        signIn(MERCHANT_EMAIL)

        val result = orders.transition(id, OrderStatus.REJECTED, Actor.MERCHANT, "  ")

        assertThat(result.isFailure).isTrue()
        assertThat(orders.observe(id).first { it != null }?.status).isEqualTo(OrderStatus.PLACED)
    }

    @Test
    fun aCustomerCancelsAPlacedOrderButNotAnAcceptedOne() = await {
        val cancelled = placeAsCustomer()
        orders.transition(cancelled, OrderStatus.CANCELLED, Actor.CUSTOMER).getOrThrow()
        assertThat(orders.observe(cancelled).first { it?.status == OrderStatus.CANCELLED }).isNotNull()

        val accepted = placeAsCustomer()
        signIn(MERCHANT_EMAIL)
        orders.transition(accepted, OrderStatus.ACCEPTED, Actor.MERCHANT).getOrThrow()
        signIn(CUSTOMER_EMAIL)

        val late = orders.transition(accepted, OrderStatus.CANCELLED, Actor.CUSTOMER)

        assertThat(late.isFailure).isTrue()
        assertThat(orders.observe(accepted).first { it != null }?.status).isEqualTo(OrderStatus.ACCEPTED)
    }

    @Test
    fun theRulesRefuseACustomerAcceptingTheirOwnOrder() = await {
        val id = placeAsCustomer()

        val result = orders.transition(id, OrderStatus.ACCEPTED, Actor.MERCHANT)

        assertThat(result.isFailure).isTrue()
        assertThat(orders.observe(id).first { it != null }?.status).isEqualTo(OrderStatus.PLACED)
    }

    private companion object {
        const val CUSTOMER_ID = "seed-customer-1"
        const val CUSTOMER_EMAIL = "customer1@otli.test"
        const val MERCHANT_1 = "seed-merchant-1"
        const val MERCHANT_EMAIL = "merchant1@otli.test"
        const val PASSWORD = "otli-demo-123"
    }
}
