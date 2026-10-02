package com.otli.app.tracking.domain

import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Test

class LiveMapAccessTest {
    private fun viewer(role: Role, uid: String = "customer-1", status: AccountStatus = AccountStatus.ACTIVE) =
        UserAccount(uid, role, status, "Someone", "$uid@otli.test", "88880000")

    private fun order(status: OrderStatus) =
        anOrder("o1", status, customerId = "customer-1", merchantId = "merchant-1", courierId = "courier-1")

    @Test
    fun theCustomerOfTheOrderSeesTheMapWhileItIsClaimedOrPickedUp() {
        assertThat(LiveMapAccess.canView(order(OrderStatus.CLAIMED), viewer(Role.CUSTOMER))).isTrue()
        assertThat(LiveMapAccess.canView(order(OrderStatus.PICKED_UP), viewer(Role.CUSTOMER))).isTrue()
    }

    @Test
    fun adminSeesTheMapOfAnyOrderBeingDelivered() {
        assertThat(LiveMapAccess.canView(order(OrderStatus.CLAIMED), viewer(Role.ADMIN, uid = "admin-1"))).isTrue()
        assertThat(LiveMapAccess.canView(order(OrderStatus.PICKED_UP), viewer(Role.ADMIN, uid = "admin-1"))).isTrue()
    }

    @Test
    fun aDifferentCustomerDoesNotSeeIt() {
        assertThat(LiveMapAccess.canView(order(OrderStatus.PICKED_UP), viewer(Role.CUSTOMER, uid = "customer-2"))).isFalse()
    }

    @Test
    fun theMerchantOfTheOrderDoesNotSeeIt() {
        assertThat(LiveMapAccess.canView(order(OrderStatus.PICKED_UP), viewer(Role.MERCHANT, uid = "merchant-1"))).isFalse()
    }

    @Test
    fun aCourierDoesNotGetTheCustomersMapEvenForTheirOwnOrder() {
        assertThat(LiveMapAccess.canView(order(OrderStatus.PICKED_UP), viewer(Role.COURIER, uid = "courier-1"))).isFalse()
    }

    @Test
    fun theRoleCannotBeBorrowedByAnotherUidOfTheOrder() {
        // A customer account whose uid equals the order's merchant or courier is still not the order's customer.
        assertThat(LiveMapAccess.canView(order(OrderStatus.CLAIMED), viewer(Role.CUSTOMER, uid = "merchant-1"))).isFalse()
        assertThat(LiveMapAccess.canView(order(OrderStatus.CLAIMED), viewer(Role.CUSTOMER, uid = "courier-1"))).isFalse()
    }

    @Test
    fun aSuspendedOrPendingAccountSeesNothing() {
        assertThat(LiveMapAccess.canView(order(OrderStatus.CLAIMED), viewer(Role.CUSTOMER, status = AccountStatus.SUSPENDED))).isFalse()
        assertThat(LiveMapAccess.canView(order(OrderStatus.CLAIMED), viewer(Role.ADMIN, uid = "admin-1", status = AccountStatus.PENDING))).isFalse()
    }

    @Test
    fun withoutAUserDocumentNothingIsShown() {
        assertThat(LiveMapAccess.canView(order(OrderStatus.CLAIMED), null)).isFalse()
    }

    @Test
    fun onlyClaimedAndPickedUpOrdersHaveAMap() {
        for (status in OrderStatus.entries - setOf(OrderStatus.CLAIMED, OrderStatus.PICKED_UP)) {
            assertThat(LiveMapAccess.canView(order(status), viewer(Role.CUSTOMER))).isFalse()
            assertThat(LiveMapAccess.canView(order(status), viewer(Role.ADMIN, uid = "admin-1"))).isFalse()
        }
    }
}
