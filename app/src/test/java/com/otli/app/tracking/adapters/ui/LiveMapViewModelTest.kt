package com.otli.app.tracking.adapters.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.AuthUser
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import com.otli.app.core.map.MapPin
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.FakeSignedInAuth
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.tracking.application.FakeLocationRepository
import com.otli.app.tracking.domain.LivePosition
import org.junit.Rule
import org.junit.Test

class LiveMapViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val orders = FakeOrderRepository()
    private val locations = FakeLocationRepository()
    private val auth = FakeSignedInAuth("customer-1")

    private val dropoff = MapPin(12.27, -86.57)
    private val courierAt = LivePosition("courier-1", 12.2656, -86.5664, accuracyMeters = 6f, updatedAtMillis = 5_000L)

    private fun account(role: Role, uid: String = "customer-1", status: AccountStatus = AccountStatus.ACTIVE) =
        UserAccount(uid, role, status, "Someone", "$uid@otli.test", "88880000")

    private fun viewModel(orderId: String? = "o1") =
        LiveMapViewModel(orders, locations, auth, SavedStateHandle(if (orderId == null) emptyMap() else mapOf("orderId" to orderId)))

    private fun order(status: OrderStatus) {
        orders.orders.value = listOf(anOrder("o1", status, customerId = "customer-1", courierId = "courier-1"))
    }

    private fun signedInAs(role: Role, uid: String, status: AccountStatus = AccountStatus.ACTIVE) {
        auth.authState.value = AuthUser(uid, "$uid@otli.test")
        auth.userDocument.value = account(role, uid, status)
    }

    // --- the owner customer ---

    @Test
    fun theOwnerSeesTheDropoffAndNoCourierUntilOnePublishes() {
        order(OrderStatus.CLAIMED)
        auth.userDocument.value = account(Role.CUSTOMER)

        val state = viewModel().uiState.value

        assertThat(state.visible).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(state.dropoff).isEqualTo(dropoff)
        assertThat(state.courier).isNull()
        assertThat(state.locationUnavailable).isFalse()
    }

    @Test
    fun theCourierMarkerFollowsTheLivePosition() {
        order(OrderStatus.PICKED_UP)
        auth.userDocument.value = account(Role.CUSTOMER)
        val viewModel = viewModel()

        locations.position.value = courierAt
        assertThat(viewModel.uiState.value.courier).isEqualTo(courierAt)

        val moved = courierAt.copy(latitude = 12.2700, longitude = -86.5700, updatedAtMillis = 15_000L)
        locations.position.value = moved
        assertThat(viewModel.uiState.value.courier).isEqualTo(moved)
        assertThat(viewModel.uiState.value.dropoff).isEqualTo(dropoff)
    }

    @Test
    fun movingFromClaimedToPickedUpKeepsTheSameLocationListener() {
        order(OrderStatus.CLAIMED)
        auth.userDocument.value = account(Role.CUSTOMER)
        val viewModel = viewModel()
        locations.position.value = courierAt

        order(OrderStatus.PICKED_UP)

        assertThat(locations.observeStarts).isEqualTo(1)
        assertThat(viewModel.uiState.value.visible).isTrue()
        assertThat(viewModel.uiState.value.courier).isEqualTo(courierAt)
    }

    @Test
    fun theMapDisappearsOnceTheOrderIsDelivered() {
        order(OrderStatus.PICKED_UP)
        auth.userDocument.value = account(Role.CUSTOMER)
        val viewModel = viewModel()
        locations.position.value = courierAt
        assertThat(viewModel.uiState.value.visible).isTrue()

        order(OrderStatus.DELIVERED)

        val state = viewModel.uiState.value
        assertThat(state.visible).isFalse()
        assertThat(state.courier).isNull()
    }

    @Test
    fun theMapIsHiddenBeforeTheOrderIsClaimed() {
        locations.position.value = courierAt
        auth.userDocument.value = account(Role.CUSTOMER)

        for (status in listOf(OrderStatus.PLACED, OrderStatus.READY)) {
            order(status)
            val state = viewModel().uiState.value
            assertThat(state.visible).isFalse()
            assertThat(state.courier).isNull()
        }
        assertThat(locations.observeStarts).isEqualTo(0)
    }

    // --- who may look ---

    @Test
    fun aDifferentCustomerIsRefusedTheLocationAndNeverEvenListens() {
        order(OrderStatus.PICKED_UP)
        locations.position.value = courierAt
        signedInAs(Role.CUSTOMER, "customer-2")

        val state = viewModel().uiState.value

        assertThat(state.visible).isFalse()
        assertThat(state.courier).isNull()
        assertThat(state.dropoff).isNull()
        assertThat(locations.observeStarts).isEqualTo(0)
    }

    @Test
    fun theMerchantAndTheCourierAreRefusedToo() {
        order(OrderStatus.PICKED_UP)
        locations.position.value = courierAt
        for ((role, uid) in listOf(Role.MERCHANT to "m1", Role.COURIER to "courier-1")) {
            signedInAs(role, uid)

            val state = viewModel().uiState.value

            assertThat(state.visible).isFalse()
            assertThat(state.courier).isNull()
        }
        assertThat(locations.observeStarts).isEqualTo(0)
    }

    @Test
    fun adminSeesTheCourierOfAnyOrderBeingDelivered() {
        order(OrderStatus.CLAIMED)
        locations.position.value = courierAt
        signedInAs(Role.ADMIN, "admin-1")

        val state = viewModel().uiState.value

        assertThat(state.visible).isTrue()
        assertThat(state.courier).isEqualTo(courierAt)
    }

    @Test
    fun aSuspendedCustomerIsRefused() {
        order(OrderStatus.CLAIMED)
        locations.position.value = courierAt
        signedInAs(Role.CUSTOMER, "customer-1", AccountStatus.SUSPENDED)

        assertThat(viewModel().uiState.value.visible).isFalse()
        assertThat(locations.observeStarts).isEqualTo(0)
    }

    @Test
    fun signingOutHidesTheMap() {
        order(OrderStatus.CLAIMED)
        auth.userDocument.value = account(Role.CUSTOMER)
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.visible).isTrue()

        auth.authState.value = null

        assertThat(viewModel.uiState.value.visible).isFalse()
    }

    // --- failures ---

    @Test
    fun anOrderThatDoesNotExistHasNoMap() {
        auth.userDocument.value = account(Role.CUSTOMER)

        val state = viewModel().uiState.value

        assertThat(state.visible).isFalse()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun aMissingOrderIdHasNoMap() {
        val state = viewModel(orderId = null).uiState.value

        assertThat(state.visible).isFalse()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun aRefusedLocationListenerKeepsTheDropoffAndSaysTheLocationIsUnavailable() {
        order(OrderStatus.CLAIMED)
        auth.userDocument.value = account(Role.CUSTOMER)
        locations.listenerError = IllegalStateException("permission denied")

        val state = viewModel().uiState.value

        assertThat(state.visible).isTrue()
        assertThat(state.dropoff).isEqualTo(dropoff)
        assertThat(state.courier).isNull()
        assertThat(state.locationUnavailable).isTrue()
    }

    @Test
    fun aFailingOrderListenerHidesTheMapInsteadOfCrashing() {
        order(OrderStatus.CLAIMED)
        auth.userDocument.value = account(Role.CUSTOMER)
        orders.listenerError = IllegalStateException("permission denied")

        val state = viewModel().uiState.value

        assertThat(state.visible).isFalse()
        assertThat(state.isLoading).isFalse()
    }
}
