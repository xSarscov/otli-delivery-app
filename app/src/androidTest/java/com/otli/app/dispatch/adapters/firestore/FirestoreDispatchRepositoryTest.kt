package com.otli.app.dispatch.adapters.firestore

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.otli.app.BuildConfig
import com.otli.app.auth.adapters.firestore.FirestoreAuthRepository
import com.otli.app.core.di.OtliFirebase
import com.otli.app.core.firebase.await
import com.otli.app.core.money.Money
import com.otli.app.dispatch.domain.ClaimDecision
import com.otli.app.dispatch.domain.ClaimDenial
import com.otli.app.ordering.adapters.firestore.FirestoreOrderRepository
import com.otli.app.ordering.adapters.firestore.FirestoreSettingsRepository
import com.otli.app.ordering.domain.Actor
import com.otli.app.ordering.domain.OrderDraft
import com.otli.app.ordering.domain.OrderItem
import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.ordering.domain.Totals
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adapter proof against the emulators. Needs the seed data (`npm --prefix backend run seed`, run by
 * `test:android`): the seeded customer, merchant and the two active couriers. The default Firebase
 * instance plays the customer, the merchant and courier 1 in turn; a second [FirebaseApp] stays signed
 * in as courier 2, so the two couriers can race for one order like two phones. Orders cannot be
 * deleted by the rules, so each run leaves its orders behind.
 *
 * Isolation: every test starts and ends by freeing both couriers ([releaseTheCouriers]), so one test
 * (or an aborted earlier run) can never leave a courier busy for the next. Both the release and the
 * assertions on stored state read with [Source.SERVER]: a snapshot listener answers from the local
 * cache first, and the cache lags behind transactions and the other app's writes, so a cache read
 * would see a stale slot or order status and skip the cleanup.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreDispatchRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val auth = OtliFirebase.auth(context)
    private val firestore = OtliFirebase.firestore(context)
    private val orders = FirestoreOrderRepository(firestore)
    private val settings = FirestoreSettingsRepository(firestore)
    private val dispatch = FirestoreDispatchRepository(firestore)

    private val secondAuth: FirebaseAuth
    private val secondFirestore: FirebaseFirestore
    private val secondDispatch: FirestoreDispatchRepository

    init {
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == SECOND_APP }
            ?: FirebaseApp.initializeApp(context, OtliFirebase.app(context).options, SECOND_APP)
        secondAuth = FirebaseAuth.getInstance(app)
        secondFirestore = FirebaseFirestore.getInstance(app)
        if (BuildConfig.OTLI_USE_EMULATOR && !emulatorWired) {
            secondAuth.useEmulator(BuildConfig.OTLI_EMULATOR_HOST, OtliFirebase.AUTH_EMULATOR_PORT)
            secondFirestore.useEmulator(BuildConfig.OTLI_EMULATOR_HOST, OtliFirebase.FIRESTORE_EMULATOR_PORT)
            emulatorWired = true
        }
        secondDispatch = FirestoreDispatchRepository(secondFirestore)
    }

    // Returns Unit so every `@Test fun x() = await { ... }` compiles to a void JUnit method.
    private fun await(block: suspend () -> Unit): Unit = runBlocking { withTimeout(120_000) { block() } }

    private suspend fun signIn(email: String) {
        auth.signOut()
        FirestoreAuthRepository(auth, firestore).login(email, PASSWORD).getOrThrow()
    }

    private suspend fun signInSecondCourier() {
        if (secondAuth.currentUser?.uid != COURIER_2) {
            secondAuth.signOut()
            FirestoreAuthRepository(secondAuth, FirebaseFirestore.getInstance(secondAuth.app)).login(COURIER_2_EMAIL, PASSWORD).getOrThrow()
        }
    }

    /** A new order taken all the way to `ready` by the seeded customer and merchant. */
    private suspend fun readyOrder(): String {
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
        return id
    }

    private suspend fun signInCourier1Online() {
        signIn(COURIER_1_EMAIL)
        dispatch.setOnline(COURIER_1, true).getOrThrow()
    }

    private suspend fun secondCourierOnline() {
        signInSecondCourier()
        secondDispatch.setOnline(COURIER_2, true).getOrThrow()
    }

    private fun firestoreOf(courierId: String) = if (courierId == COURIER_2) secondFirestore else firestore

    /** The order's status as the server holds it, readable by the signed-in claimer or any courier while it is ready. */
    private suspend fun status(orderId: String): OrderStatus? =
        firestore.collection("orders").document(orderId).get(Source.SERVER).await().getString("status")?.let(OrderStatus::fromWire)

    /** The courier's slot as the server holds it; the signed-in user of that courier's app must be the courier. */
    private suspend fun slotOf(courierId: String): String? =
        firestoreOf(courierId).collection("couriers").document(courierId).get(Source.SERVER).await().getString("activeOrderId")

    @Before
    fun startFromFreeCouriers() = await { releaseTheCouriers(strict = true) }

    @After
    fun freeTheCouriers() = runBlocking {
        // Best effort: a failure here must not hide the test's own verdict, and the next @Before retries it.
        runCatching { withTimeout(60_000) { release(auth, dispatch, COURIER_1, COURIER_1_EMAIL, strict = false) } }
        runCatching { withTimeout(60_000) { release(secondAuth, secondDispatch, COURIER_2, COURIER_2_EMAIL, strict = false) } }
        auth.signOut()
        Unit
    }

    private suspend fun releaseTheCouriers(strict: Boolean) {
        release(auth, dispatch, COURIER_1, COURIER_1_EMAIL, strict)
        release(secondAuth, secondDispatch, COURIER_2, COURIER_2_EMAIL, strict)
    }

    private fun Result<Unit>.requiredWhen(strict: Boolean) {
        if (strict) getOrThrow()
    }

    /**
     * Delivers whatever the courier still holds and goes offline, so the next test starts from the seed
     * state. Leftovers of an aborted earlier run are cleaned the same way. A [strict] release fails the
     * test that is starting instead of letting it fail later for an unrelated reason.
     */
    private suspend fun release(courierAuth: FirebaseAuth, repo: FirestoreDispatchRepository, courierId: String, email: String, strict: Boolean) {
        if (courierAuth.currentUser?.uid != courierId) {
            courierAuth.signOut()
            FirestoreAuthRepository(courierAuth, FirebaseFirestore.getInstance(courierAuth.app)).login(email, PASSWORD).getOrThrow()
        }
        val active = slotOf(courierId)
        if (active != null) {
            val orderStatus = firestoreOf(courierId).collection("orders").document(active).get(Source.SERVER).await().getString("status")
            if (orderStatus == OrderStatus.CLAIMED.wire) repo.markPickedUp(active, courierId).requiredWhen(strict)
            if (orderStatus == OrderStatus.CLAIMED.wire || orderStatus == OrderStatus.PICKED_UP.wire) {
                repo.markDelivered(active, courierId).requiredWhen(strict)
            }
        }
        repo.setOnline(courierId, false).requiredWhen(strict)
    }

    // --- availability ---

    @Test
    fun goingOnlineAndOfflineIsObservedBack() = await {
        signIn(COURIER_1_EMAIL)

        dispatch.setOnline(COURIER_1, true).getOrThrow()
        assertThat(dispatch.observeCourier(COURIER_1).first { it?.isOnline == true }?.activeOrderId).isNull()

        dispatch.setOnline(COURIER_1, false).getOrThrow()
        assertThat(dispatch.observeCourier(COURIER_1).first { it?.isOnline == false }).isNotNull()
    }

    @Test
    fun aCourierCannotSwitchAnotherCourierOnline() = await {
        signIn(COURIER_1_EMAIL)

        val result = dispatch.setOnline(COURIER_2, true)

        assertThat(result.isFailure).isTrue()
    }

    // --- the pool ---

    @Test
    fun aReadyOrderAppearsInThePoolOldestFirst() = await {
        val first = readyOrder()
        val second = readyOrder()
        signIn(COURIER_1_EMAIL)

        val pool = dispatch.observePool().first { list -> list.any { it.id == first } && list.any { it.id == second } }

        val ids = pool.map { it.id }
        assertThat(ids.indexOf(first)).isLessThan(ids.indexOf(second))
        val listed = pool.first { it.id == first }
        assertThat(listed.merchantName).isEqualTo("Comedor Doña Marta")
        assertThat(listed.dropoff.reference).isEqualTo("Casa azul")
        assertThat(listed.readyAtMillis).isGreaterThan(0L)
    }

    // --- the claim ---

    @Test
    fun anOnlineFreeCourierClaimsAReadyOrderAndTheOrderAndTheSlotBothChange() = await {
        val id = readyOrder()
        signInCourier1Online()

        val decision = dispatch.claim(id, COURIER_1).getOrThrow()

        assertThat(decision).isEqualTo(ClaimDecision.Allowed)
        val order = orders.observe(id).first { it?.status == OrderStatus.CLAIMED }!!
        assertThat(order.courierId).isEqualTo(COURIER_1)
        assertThat(dispatch.observeCourier(COURIER_1).first { it?.activeOrderId == id }).isNotNull()
        assertThat(dispatch.observePool().first { list -> list.none { it.id == id } }).isNotNull()
    }

    @Test
    fun twoCouriersClaimingTheSameOrderExactlyOneWinsAndTheOtherIsToldItWasTaken() = await {
        val id = readyOrder()
        signInCourier1Online()
        secondCourierOnline()

        val decisions = coroutineScope {
            val first = async { dispatch.claim(id, COURIER_1).getOrThrow() }
            val second = async { secondDispatch.claim(id, COURIER_2).getOrThrow() }
            listOf(first.await(), second.await())
        }

        assertThat(decisions.count { it == ClaimDecision.Allowed }).isEqualTo(1)
        assertThat(decisions.count { it == ClaimDecision.Denied(ClaimDenial.ALREADY_CLAIMED) }).isEqualTo(1)
        val winner = if (decisions[0] == ClaimDecision.Allowed) COURIER_1 else COURIER_2
        val loser = if (winner == COURIER_1) COURIER_2 else COURIER_1
        assertThat(orders.observe(id).first { it?.status == OrderStatus.CLAIMED }?.courierId).isEqualTo(winner)
        assertThat(dispatch.observeCourier(winner).first { it?.activeOrderId == id }).isNotNull()
        assertThat(slotOf(loser)).isNull()
    }

    @Test
    fun claimingAnOrderSomeoneElseAlreadyClaimedIsADenialNotAnError() = await {
        val id = readyOrder()
        signInCourier1Online()
        secondCourierOnline()
        assertThat(dispatch.claim(id, COURIER_1).getOrThrow()).isEqualTo(ClaimDecision.Allowed)

        val late = secondDispatch.claim(id, COURIER_2)

        assertThat(late.getOrNull()).isEqualTo(ClaimDecision.Denied(ClaimDenial.ALREADY_CLAIMED))
        assertThat(slotOf(COURIER_2)).isNull()
    }

    @Test
    fun anOfflineCourierIsDeniedTheClaim() = await {
        val id = readyOrder()
        signIn(COURIER_1_EMAIL)
        dispatch.setOnline(COURIER_1, false).getOrThrow()

        val decision = dispatch.claim(id, COURIER_1).getOrThrow()

        assertThat(decision).isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_OFFLINE))
        assertThat(status(id)).isEqualTo(OrderStatus.READY)
    }

    @Test
    fun aCourierWithAnActiveOrderIsDeniedASecondClaimAndCannotGoOffline() = await {
        val first = readyOrder()
        val second = readyOrder()
        signInCourier1Online()
        assertThat(dispatch.claim(first, COURIER_1).getOrThrow()).isEqualTo(ClaimDecision.Allowed)

        val decision = dispatch.claim(second, COURIER_1).getOrThrow()
        val offline = dispatch.setOnline(COURIER_1, false)

        assertThat(decision).isEqualTo(ClaimDecision.Denied(ClaimDenial.COURIER_BUSY))
        assertThat(status(second)).isEqualTo(OrderStatus.READY)
        assertThat(offline.isFailure).isTrue()
    }

    // --- pickup and delivery ---

    @Test
    fun theClaimingCourierPicksUpAndDeliversAndTheSlotIsFreed() = await {
        val id = readyOrder()
        signInCourier1Online()
        assertThat(dispatch.claim(id, COURIER_1).getOrThrow()).isEqualTo(ClaimDecision.Allowed)

        dispatch.markPickedUp(id, COURIER_1).getOrThrow()
        assertThat(orders.observe(id).first { it?.status == OrderStatus.PICKED_UP }).isNotNull()
        dispatch.markDelivered(id, COURIER_1).getOrThrow()

        assertThat(orders.observe(id).first { it?.status == OrderStatus.DELIVERED }).isNotNull()
        assertThat(slotOf(COURIER_1)).isNull()
        assertThat(dispatch.observeCourier(COURIER_1).first { it?.activeOrderId == null }?.isOnline).isTrue()
    }

    @Test
    fun deliveringBeforePickupIsRefusedAndKeepsTheSlot() = await {
        val id = readyOrder()
        signInCourier1Online()
        assertThat(dispatch.claim(id, COURIER_1).getOrThrow()).isEqualTo(ClaimDecision.Allowed)

        val result = dispatch.markDelivered(id, COURIER_1)

        assertThat(result.isFailure).isTrue()
        assertThat(status(id)).isEqualTo(OrderStatus.CLAIMED)
        assertThat(slotOf(COURIER_1)).isEqualTo(id)
    }

    @Test
    fun anotherCourierCannotPickUpSomeoneElsesOrder() = await {
        val id = readyOrder()
        signInCourier1Online()
        secondCourierOnline()
        assertThat(dispatch.claim(id, COURIER_1).getOrThrow()).isEqualTo(ClaimDecision.Allowed)

        val result = secondDispatch.markPickedUp(id, COURIER_2)

        assertThat(result.isFailure).isTrue()
        assertThat(status(id)).isEqualTo(OrderStatus.CLAIMED)
    }

    private companion object {
        const val SECOND_APP = "courier-b"
        var emulatorWired = false
        const val PASSWORD = "otli-demo-123"
        const val CUSTOMER_ID = "seed-customer-1"
        const val CUSTOMER_EMAIL = "customer1@otli.test"
        const val MERCHANT_1 = "seed-merchant-1"
        const val MERCHANT_EMAIL = "merchant1@otli.test"
        const val COURIER_1 = "seed-courier-1"
        const val COURIER_1_EMAIL = "courier1@otli.test"
        const val COURIER_2 = "seed-courier-2"
        const val COURIER_2_EMAIL = "courier2@otli.test"
    }
}
