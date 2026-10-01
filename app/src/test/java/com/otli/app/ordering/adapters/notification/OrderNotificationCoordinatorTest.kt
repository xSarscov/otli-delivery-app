package com.otli.app.ordering.adapters.notification

import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.application.AuthRepository
import com.otli.app.auth.application.ObserveSessionUseCase
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.AuthUser
import com.otli.app.auth.domain.MerchantStoreDetails
import com.otli.app.auth.domain.ProfileFields
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import com.otli.app.core.notification.OrderNotice
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class ScriptedAuth : AuthRepository {
    val authState = MutableStateFlow<AuthUser?>(null)
    private val documents = mutableMapOf<String, MutableStateFlow<UserAccount?>>()

    fun signIn(uid: String, role: Role, status: AccountStatus = AccountStatus.ACTIVE) {
        documents.getOrPut(uid) { MutableStateFlow(null) }.value = UserAccount(uid, role, status, "Name $uid", "$uid@otli.test", "8888-0000")
        authState.value = AuthUser(uid, "$uid@otli.test")
    }

    var authFeed: Flow<AuthUser?> = authState

    override fun observeAuthState(): Flow<AuthUser?> = authFeed

    override fun observeUserDocument(uid: String): Flow<UserAccount?> = documents.getOrPut(uid) { MutableStateFlow(null) }

    override suspend fun register(
        email: String,
        password: String,
        role: Role,
        profileFields: ProfileFields,
        merchantStore: MerchantStoreDetails?,
    ) = Result.success(Unit)

    override suspend fun login(email: String, password: String) = Result.success(Unit)

    override suspend fun logout() {
        authState.value = null
    }
}

private class RecordingPort : com.otli.app.core.notification.NotificationPort {
    val notices = mutableListOf<OrderNotice>()

    override fun notify(notice: OrderNotice) {
        notices += notice
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OrderNotificationCoordinatorTest {
    private val auth = ScriptedAuth()
    private val orders = FakeOrderRepository()
    private val port = RecordingPort()
    private val coordinator = OrderNotificationCoordinator(ObserveSessionUseCase(auth), auth, LocalOrderNotifier(orders, port))

    @Test
    fun anActiveCustomerIsToldWhenTheirOrderChanges() = runTest(UnconfinedTestDispatcher()) {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED, customerId = "c1"))
        auth.signIn("c1", Role.CUSTOMER)
        coordinator.start(backgroundScope)

        orders.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED, customerId = "c1"))

        assertThat(port.notices.map { it.orderId }).containsExactly("o1")
    }

    @Test
    fun anActiveMerchantIsToldAboutANewOrderForTheirStoreOnly() = runTest(UnconfinedTestDispatcher()) {
        auth.signIn("m1", Role.MERCHANT)
        coordinator.start(backgroundScope)

        orders.orders.value = listOf(
            anOrder("o1", OrderStatus.PLACED, merchantId = "m1"),
            anOrder("o2", OrderStatus.PLACED, merchantId = "m2"),
        )

        assertThat(port.notices).hasSize(1)
        assertThat(port.notices.single()).isInstanceOf(OrderNotice.NewOrder::class.java)
        assertThat(port.notices.single().orderId).isEqualTo("o1")
    }

    @Test
    fun nobodyIsToldWhileSignedOutOrBeforeTheAccountIsApproved() = runTest(UnconfinedTestDispatcher()) {
        coordinator.start(backgroundScope)
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))

        orders.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED), anOrder("o2", OrderStatus.PLACED))
        auth.signIn("m1", Role.MERCHANT, AccountStatus.PENDING)
        orders.orders.value = listOf(anOrder("o3", OrderStatus.PLACED))

        assertThat(port.notices).isEmpty()
    }

    @Test
    fun courierAndAdminAccountsAreNotWatched() = runTest(UnconfinedTestDispatcher()) {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED, customerId = "u1", merchantId = "u1"))
        for (role in listOf(Role.COURIER, Role.ADMIN)) {
            auth.signIn("u1", role)
            coordinator.start(backgroundScope)
        }

        orders.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED, customerId = "u1", merchantId = "u1"), anOrder("o2", OrderStatus.PLACED, customerId = "u1", merchantId = "u1"))

        assertThat(port.notices).isEmpty()
    }

    @Test
    fun aFailingAuthListenerEndsTheWatchWithoutCrashing() = runTest(UnconfinedTestDispatcher()) {
        auth.authFeed = flow { throw IllegalStateException("auth listener failed") }

        coordinator.start(backgroundScope)

        assertThat(port.notices).isEmpty()
    }

    @Test
    fun theWatchStopsWhenTheUserSignsOut() = runTest(UnconfinedTestDispatcher()) {
        auth.signIn("m1", Role.MERCHANT)
        coordinator.start(backgroundScope)
        auth.logout()

        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))

        assertThat(port.notices).isEmpty()
    }

    @Test
    fun aNewUserAfterSignOutIsWatchedAsTheirOwnRole() = runTest(UnconfinedTestDispatcher()) {
        auth.signIn("m1", Role.MERCHANT)
        coordinator.start(backgroundScope)
        auth.logout()
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED, customerId = "c1"))
        auth.signIn("c1", Role.CUSTOMER)

        orders.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED, customerId = "c1"))

        assertThat(port.notices.single()).isInstanceOf(OrderNotice.StatusChanged::class.java)
    }

    @Test
    fun aFailingListenerDoesNotStopTheCoordinatorFromWatchingTheNextUser() = runTest(UnconfinedTestDispatcher()) {
        orders.listenerError = IllegalStateException("PERMISSION_DENIED")
        auth.signIn("m1", Role.MERCHANT)
        coordinator.start(backgroundScope)
        auth.logout()
        orders.listenerError = null
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED, customerId = "c1"))
        auth.signIn("c1", Role.CUSTOMER)

        orders.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED, customerId = "c1"))

        assertThat(port.notices).hasSize(1)
    }
}
