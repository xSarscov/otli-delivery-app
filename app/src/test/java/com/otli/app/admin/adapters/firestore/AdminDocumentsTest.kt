package com.otli.app.admin.adapters.firestore

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.FieldValue
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import com.otli.app.core.money.Money
import org.junit.Test

class AdminDocumentsTest {
    // --- the fee ---

    @Test
    fun theFeeDocumentCarriesTheAmountTheServerTimeAndTheAdmin() {
        val data = AdminDocuments.feeData(Money(4500), "admin-1")

        assertThat(data).containsExactly(
            "deliveryFeeCents", 4500L,
            "updatedAt", FieldValue.serverTimestamp(),
            "updatedBy", "admin-1",
        )
    }

    // --- account status ---

    @Test
    fun theAccountUpdateChangesOnlyTheStatusInTheWireSpelling() {
        assertThat(AdminDocuments.userStatusUpdate(AccountStatus.ACTIVE)).containsExactly("status", "active")
        assertThat(AdminDocuments.userStatusUpdate(AccountStatus.SUSPENDED)).containsExactly("status", "suspended")
        assertThat(AdminDocuments.userStatusUpdate(AccountStatus.PENDING)).containsExactly("status", "pending")
    }

    @Test
    fun theMerchantMirrorFollowsTheStatusAndStampsTheServerTime() {
        assertThat(AdminDocuments.merchantMirrorUpdate(AccountStatus.SUSPENDED))
            .containsExactly("status", "suspended", "updatedAt", FieldValue.serverTimestamp())
    }

    @Test
    fun onlyAMerchantHasAStorefrontMirror() {
        assertThat(AdminDocuments.hasMirror(Role.MERCHANT)).isTrue()
        for (role in Role.entries - Role.MERCHANT) assertThat(AdminDocuments.hasMirror(role)).isFalse()
    }

    // --- release and cancel ---

    @Test
    fun theReleasePutsTheOrderBackToReadyWithoutACourierAndFreesTheSlot() {
        assertThat(AdminDocuments.releaseOrderUpdate())
            .containsExactly("status", "ready", "courierId", null, "updatedAt", FieldValue.serverTimestamp())
        assertThat(AdminDocuments.releaseCourierUpdate())
            .containsExactly("activeOrderId", null, "updatedAt", FieldValue.serverTimestamp())
    }

    @Test
    fun theCancellationRecordsWhoCancelledWhyAndWhen() {
        assertThat(AdminDocuments.cancelUpdate("Store never answered")).containsExactly(
            "status", "cancelled",
            "cancelledBy", "admin",
            "cancelReason", "Store never answered",
            "cancelledAt", FieldValue.serverTimestamp(),
            "updatedAt", FieldValue.serverTimestamp(),
        )
    }

    @Test
    fun theCourierToFreeIsTheOneNamedOnTheClaimedOrder() {
        assertThat(AdminDocuments.courierToFree(mapOf("status" to "claimed", "courierId" to "courier-1"))).isEqualTo("courier-1")
        assertThat(AdminDocuments.courierToFree(mapOf("status" to "claimed", "courierId" to "courier-2"))).isEqualTo("courier-2")
    }

    @Test
    fun anOrderThatIsNotClaimedOrNamesNoCourierHasNoCourierToFree() {
        assertThat(AdminDocuments.courierToFree(null)).isNull()
        assertThat(AdminDocuments.courierToFree(mapOf("status" to "ready", "courierId" to null))).isNull()
        assertThat(AdminDocuments.courierToFree(mapOf("status" to "picked_up", "courierId" to "courier-1"))).isNull()
        assertThat(AdminDocuments.courierToFree(mapOf("status" to "claimed", "courierId" to null))).isNull()
        assertThat(AdminDocuments.courierToFree(mapOf("status" to "claimed", "courierId" to ""))).isNull()
    }

    // --- reading accounts ---

    private val merchantDoc: Map<String, Any?> = mapOf(
        "role" to "merchant", "status" to "pending", "displayName" to "Nuevo Comercio", "email" to "m@otli.test", "phone" to "+50588880103",
    )

    @Test
    fun aMerchantOrCourierAccountIsReadBack() {
        assertThat(AdminDocuments.accountFrom("m1", merchantDoc))
            .isEqualTo(UserAccount("m1", Role.MERCHANT, AccountStatus.PENDING, "Nuevo Comercio", "m@otli.test", "+50588880103"))
        val courier = AdminDocuments.accountFrom("c1", merchantDoc + mapOf("role" to "courier", "status" to "suspended"))
        assertThat(courier?.role).isEqualTo(Role.COURIER)
        assertThat(courier?.status).isEqualTo(AccountStatus.SUSPENDED)
    }

    @Test
    fun aDocumentWithAnUnknownRoleOrStatusIsNotAnAccountToManage() {
        assertThat(AdminDocuments.accountFrom("x", merchantDoc + mapOf("role" to "wizard"))).isNull()
        assertThat(AdminDocuments.accountFrom("x", merchantDoc + mapOf("status" to "banned"))).isNull()
        assertThat(AdminDocuments.accountFrom("x", merchantDoc - "role")).isNull()
        assertThat(AdminDocuments.accountFrom("x", merchantDoc - "status")).isNull()
    }

    @Test
    fun missingProfileTextReadsAsEmpty() {
        val account = AdminDocuments.accountFrom("m1", mapOf("role" to "merchant", "status" to "active"))

        assertThat(account?.displayName).isEmpty()
        assertThat(account?.email).isEmpty()
        assertThat(account?.phone).isEmpty()
    }
}
