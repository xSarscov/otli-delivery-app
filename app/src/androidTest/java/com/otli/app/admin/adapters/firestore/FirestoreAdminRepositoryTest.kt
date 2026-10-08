package com.otli.app.admin.adapters.firestore

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.Source
import com.otli.app.auth.adapters.firestore.FirestoreAuthRepository
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import com.otli.app.core.di.OtliFirebase
import com.otli.app.core.firebase.await
import com.otli.app.core.money.Money
import com.otli.app.dispatch.adapters.firestore.FirestoreDispatchRepository
import com.otli.app.ordering.adapters.firestore.FirestoreOrderRepository
import com.otli.app.ordering.adapters.firestore.FirestoreSettingsRepository
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adapter proof against the emulators. Needs the seed data (`npm --prefix backend run seed`, run by
 * `test:android`): the seeded admin, customer, merchant 1, courier 1 and the pending merchant and
 * courier. The default Firebase instance plays each role in turn, signing in again to switch.
 *
 * Isolation: every test starts and ends by restoring the seed state ([resetSeedState]): the fee,
 * the two pending accounts and courier 1 (offline and free). Assertions on stored state read with
 * [Source.SERVER] because a snapshot listener answers from the local cache first, and what another
 * user's account must see is read after signing in as that user. Orders cannot be deleted by the
 * rules, so each run leaves its orders behind.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreAdminRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val auth = OtliFirebase.auth(context)
    private val firestore = OtliFirebase.firestore(context)
    private val admin = FirestoreAdminRepository(firestore, auth)
    private val orders = FirestoreOrderRepository(firestore)
    private val settings = FirestoreSettingsRepository(firestore)
    private val dispatch = FirestoreDispatchRepository(firestore)

    // Returns Unit so every `@Test fun x() = await { ... }` compiles to a void JUnit method.
    private fun await(block: suspend () -> Unit): Unit = runBlocking { withTimeout(120_000) { block() } }

    private suspend fun signIn(email: String) {
        auth.signOut()
        FirestoreAuthRepository(auth, firestore).login(email, PASSWORD).getOrThrow()
    }

    private suspend fun signInAdmin() = signIn(ADMIN_EMAIL)

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

    private suspend fun claimedOrder(): String {
        val id = readyOrder()
        signIn(COURIER_1_EMAIL)
        dispatch.setOnline(COURIER_1, true).getOrThrow()
        dispatch.claim(id, COURIER_1).getOrThrow()
        return id
    }

    private suspend fun orderField(id: String, field: String): Any? =
        firestore.collection("orders").document(id).get(Source.SERVER).await().get(field)

    private suspend fun slotOfCourier1(): String? =
        firestore.collection("couriers").document(COURIER_1).get(Source.SERVER).await().getString("activeOrderId")

    private suspend fun statusOf(collection: String, uid: String): String? =
        firestore.collection(collection).document(uid).get(Source.SERVER).await().getString("status")

    @Before
    fun startFromTheSeedState() = await { resetSeedState(strict = true) }

    @After
    fun restoreTheSeedState() = runBlocking {
        // Best effort: a failure here must not hide the test's own verdict, and the next @Before retries it.
        runCatching { withTimeout(60_000) { resetSeedState(strict = false) } }
        auth.signOut()
        Unit
    }

    /** Frees courier 1 (releasing its claim as Admin if needed), restores the fee and re-pends the two accounts. */
    private suspend fun resetSeedState(strict: Boolean) {
        signInAdmin()
        val held = firestore.collection("couriers").document(COURIER_1).get(Source.SERVER).await().getString("activeOrderId")
        if (held != null) {
            val status = orderField(held, "status")
            if (status == OrderStatus.CLAIMED.wire) admin.releaseClaim(held).required(strict)
            if (status == OrderStatus.PICKED_UP.wire) strictFail(strict, "courier 1 holds a picked-up order $held")
        }
        admin.setDeliveryFee(SEED_FEE).required(strict)
        admin.setAccountStatus(PENDING_MERCHANT, Role.MERCHANT, AccountStatus.PENDING).required(strict)
        admin.setAccountStatus(PENDING_COURIER, Role.COURIER, AccountStatus.PENDING).required(strict)
        signIn(COURIER_1_EMAIL)
        dispatch.setOnline(COURIER_1, false).required(strict)
    }

    private fun Result<Unit>.required(strict: Boolean) {
        if (strict) getOrThrow()
    }

    private fun strictFail(strict: Boolean, message: String) {
        if (strict) error(message)
    }

    // --- the fee ---

    @Test
    fun theFeeIsSavedAndReadBackByEveryOrderPlacedAfterwards() = await {
        signInAdmin()

        admin.setDeliveryFee(Money(4500)).getOrThrow()

        val stored = firestore.collection("settings").document("app").get(Source.SERVER).await()
        assertThat(stored.getLong("deliveryFeeCents")).isEqualTo(4500L)
        assertThat(stored.getString("updatedBy")).isEqualTo(ADMIN_ID)
        signIn(CUSTOMER_EMAIL)
        assertThat(settings.deliveryFee().getOrThrow()).isEqualTo(Money(4500))
    }

    @Test
    fun aCourierCannotChangeTheFee() = await {
        signIn(COURIER_1_EMAIL)

        assertThat(admin.setDeliveryFee(Money(1)).isFailure).isTrue()
    }

    // --- approvals ---

    @Test
    fun approvingAPendingMerchantMovesTheAccountAndItsStorefrontMirrorTogether() = await {
        signInAdmin()

        admin.setAccountStatus(PENDING_MERCHANT, Role.MERCHANT, AccountStatus.ACTIVE).getOrThrow()

        assertThat(statusOf("users", PENDING_MERCHANT)).isEqualTo("active")
        assertThat(statusOf("merchants", PENDING_MERCHANT)).isEqualTo("active")

        admin.setAccountStatus(PENDING_MERCHANT, Role.MERCHANT, AccountStatus.SUSPENDED).getOrThrow()

        assertThat(statusOf("users", PENDING_MERCHANT)).isEqualTo("suspended")
        assertThat(statusOf("merchants", PENDING_MERCHANT)).isEqualTo("suspended")
    }

    @Test
    fun approvingAPendingCourierMovesItsAccount() = await {
        signInAdmin()

        admin.setAccountStatus(PENDING_COURIER, Role.COURIER, AccountStatus.ACTIVE).getOrThrow()

        assertThat(statusOf("users", PENDING_COURIER)).isEqualTo("active")
    }

    @Test
    fun theManagedAccountsListHoldsMerchantsAndCouriersOnlyAndShowsThePendingOnes() = await {
        signInAdmin()

        val accounts = admin.observeManagedAccounts().first { list -> list.any { it.uid == PENDING_MERCHANT } && list.any { it.uid == PENDING_COURIER } }

        assertThat(accounts.map { it.role }.toSet()).containsExactly(Role.MERCHANT, Role.COURIER)
        assertThat(accounts.first { it.uid == PENDING_MERCHANT }.status).isEqualTo(AccountStatus.PENDING)
        assertThat(accounts.none { it.uid == ADMIN_ID || it.uid == CUSTOMER_ID }).isTrue()
    }

    @Test
    fun aCustomerCannotApproveAnAccount() = await {
        signIn(CUSTOMER_EMAIL)

        assertThat(admin.setAccountStatus(PENDING_MERCHANT, Role.MERCHANT, AccountStatus.ACTIVE).isFailure).isTrue()
    }

    // --- releasing and cancelling ---

    @Test
    fun releasingAClaimPutsTheOrderBackInThePoolAndFreesTheCourierInOneWrite() = await {
        val id = claimedOrder()
        signInAdmin()
        assertThat(admin.observeStuckOrders().first { list -> list.any { it.id == id && it.status == OrderStatus.CLAIMED } }).isNotEmpty()

        admin.releaseClaim(id).getOrThrow()

        assertThat(orderField(id, "status")).isEqualTo("ready")
        assertThat(orderField(id, "courierId")).isNull()
        assertThat(slotOfCourier1()).isNull()
    }

    @Test
    fun releasingAnOrderThatIsNotClaimedFailsAndChangesNothing() = await {
        val id = readyOrder()
        signInAdmin()

        assertThat(admin.releaseClaim(id).isFailure).isTrue()

        assertThat(orderField(id, "status")).isEqualTo("ready")
    }

    @Test
    fun cancellingAReadyOrderRecordsTheReasonAndTheCustomerReadsIt() = await {
        val id = readyOrder()
        signInAdmin()
        assertThat(admin.observeAllOrders().first { list -> list.any { it.id == id } }).isNotEmpty()

        admin.cancelOrder(id, "Store closed early").getOrThrow()

        assertThat(orderField(id, "status")).isEqualTo("cancelled")
        assertThat(orderField(id, "cancelledBy")).isEqualTo("admin")
        signIn(CUSTOMER_EMAIL)
        assertThat(orderField(id, "cancelReason")).isEqualTo("Store closed early")
    }

    @Test
    fun aClaimedOrderCannotBeCancelledUntilItIsReleased() = await {
        val id = claimedOrder()
        signInAdmin()

        assertThat(admin.cancelOrder(id, "Reason").isFailure).isTrue()
        assertThat(orderField(id, "status")).isEqualTo("claimed")

        admin.releaseClaim(id).getOrThrow()
        admin.cancelOrder(id, "Courier unreachable").getOrThrow()

        assertThat(orderField(id, "status")).isEqualTo("cancelled")
    }

    @Test
    fun aMerchantCannotCancelAnOrderOnTheAdminsBehalf() = await {
        val id = readyOrder()

        assertThat(admin.cancelOrder(id, "Reason").isFailure).isTrue()

        assertThat(orderField(id, "status")).isEqualTo("ready")
    }

    private companion object {
        const val PASSWORD = "otli-demo-123"
        const val ADMIN_ID = "seed-admin"
        const val ADMIN_EMAIL = "admin@otli.test"
        const val CUSTOMER_ID = "seed-customer-1"
        const val CUSTOMER_EMAIL = "customer1@otli.test"
        const val MERCHANT_1 = "seed-merchant-1"
        const val MERCHANT_EMAIL = "merchant1@otli.test"
        const val COURIER_1 = "seed-courier-1"
        const val COURIER_1_EMAIL = "courier1@otli.test"
        const val PENDING_MERCHANT = "seed-merchant-pending"
        const val PENDING_COURIER = "seed-courier-pending"
        val SEED_FEE = Money(3000)
    }
}
