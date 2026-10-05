package com.otli.app.admin.application

import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import com.otli.app.core.money.Money
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AdminUseCasesTest {
    private val admin = FakeAdminRepository()

    private val done = AdminActionResult.Done

    private fun rejected(reason: AdminRejection) = AdminActionResult.Rejected(reason)

    // --- approving and reactivating ---

    @Test
    fun aPendingMerchantOrCourierIsApprovedIntoActive() = runTest {
        val approve = ApproveAccount(admin)

        assertThat(approve(anAccount("m1", Role.MERCHANT, AccountStatus.PENDING))).isEqualTo(done)
        assertThat(approve(anAccount("c1", Role.COURIER, AccountStatus.PENDING))).isEqualTo(done)

        assertThat(admin.statusChanges).containsExactly(
            FakeAdminRepository.StatusChange("m1", Role.MERCHANT, AccountStatus.ACTIVE),
            FakeAdminRepository.StatusChange("c1", Role.COURIER, AccountStatus.ACTIVE),
        ).inOrder()
    }

    @Test
    fun aSuspendedAccountIsReactivatedThroughTheSameUseCase() = runTest {
        val result = ApproveAccount(admin)(anAccount("c1", Role.COURIER, AccountStatus.SUSPENDED))

        assertThat(result).isEqualTo(done)
        assertThat(admin.statusChanges).containsExactly(FakeAdminRepository.StatusChange("c1", Role.COURIER, AccountStatus.ACTIVE))
    }

    @Test
    fun anActiveAccountCannotBeApprovedAgain() = runTest {
        val result = ApproveAccount(admin)(anAccount(status = AccountStatus.ACTIVE))

        assertThat(result).isEqualTo(rejected(AdminRejection.INVALID_STATUS_CHANGE))
        assertThat(admin.statusChanges).isEmpty()
    }

    @Test
    fun customersAndAdminsAreNotApprovedOrSuspended() = runTest {
        for (role in listOf(Role.CUSTOMER, Role.ADMIN)) {
            assertThat(ApproveAccount(admin)(anAccount(role = role, status = AccountStatus.PENDING)))
                .isEqualTo(rejected(AdminRejection.ROLE_NOT_MANAGED))
            assertThat(SuspendAccount(admin)(anAccount(role = role, status = AccountStatus.ACTIVE)))
                .isEqualTo(rejected(AdminRejection.ROLE_NOT_MANAGED))
        }
        assertThat(admin.statusChanges).isEmpty()
    }

    @Test
    fun aFailedStatusWriteIsReportedAsFailed() = runTest {
        admin.writeFailure = IOException("offline")

        assertThat(ApproveAccount(admin)(anAccount())).isEqualTo(AdminActionResult.Failed)
        assertThat(SuspendAccount(admin)(anAccount(status = AccountStatus.ACTIVE))).isEqualTo(AdminActionResult.Failed)
    }

    // --- suspending ---

    @Test
    fun anActiveMerchantOrCourierIsSuspended() = runTest {
        val suspend = SuspendAccount(admin)

        assertThat(suspend(anAccount("m1", Role.MERCHANT, AccountStatus.ACTIVE))).isEqualTo(done)
        assertThat(suspend(anAccount("c1", Role.COURIER, AccountStatus.ACTIVE))).isEqualTo(done)

        assertThat(admin.statusChanges).containsExactly(
            FakeAdminRepository.StatusChange("m1", Role.MERCHANT, AccountStatus.SUSPENDED),
            FakeAdminRepository.StatusChange("c1", Role.COURIER, AccountStatus.SUSPENDED),
        ).inOrder()
    }

    @Test
    fun onlyAnActiveAccountCanBeSuspended() = runTest {
        for (status in listOf(AccountStatus.PENDING, AccountStatus.SUSPENDED)) {
            assertThat(SuspendAccount(admin)(anAccount(status = status))).isEqualTo(rejected(AdminRejection.INVALID_STATUS_CHANGE))
        }
        assertThat(admin.statusChanges).isEmpty()
    }

    // --- the fee ---

    @Test
    fun aPositiveFeeIsSaved() = runTest {
        val update = UpdateFee(admin)

        assertThat(update(Money(3000))).isEqualTo(done)
        assertThat(update(Money(1))).isEqualTo(done)

        assertThat(admin.fees).containsExactly(Money(3000), Money(1)).inOrder()
    }

    @Test
    fun aZeroFeeIsRefusedAndNothingIsSaved() = runTest {
        assertThat(UpdateFee(admin)(Money(0))).isEqualTo(rejected(AdminRejection.FEE_NOT_POSITIVE))
        assertThat(admin.fees).isEmpty()
    }

    @Test
    fun aFailedFeeWriteIsReportedAsFailed() = runTest {
        admin.writeFailure = IOException("denied")

        assertThat(UpdateFee(admin)(Money(3000))).isEqualTo(AdminActionResult.Failed)
    }

    // --- releasing a claim ---

    @Test
    fun aClaimedOrderIsReleased() = runTest {
        val result = ReleaseClaim(admin)(anOrder("o1", OrderStatus.CLAIMED, courierId = "courier-1"))

        assertThat(result).isEqualTo(done)
        assertThat(admin.releases).containsExactly("o1")
    }

    @Test
    fun onlyAClaimedOrderCanBeReleased() = runTest {
        val others = OrderStatus.entries - OrderStatus.CLAIMED
        assertThat(others).isNotEmpty()
        for (status in others) {
            assertThat(ReleaseClaim(admin)(anOrder("o-$status", status))).isEqualTo(rejected(AdminRejection.ORDER_NOT_RELEASABLE))
        }
        assertThat(admin.releases).isEmpty()
    }

    @Test
    fun aFailedReleaseIsReportedAsFailed() = runTest {
        admin.writeFailure = IOException("offline")

        assertThat(ReleaseClaim(admin)(anOrder("o1", OrderStatus.CLAIMED))).isEqualTo(AdminActionResult.Failed)
    }

    // --- cancelling ---

    @Test
    fun anOrderNoCourierHoldsIsCancelledWithTheTrimmedReason() = runTest {
        val cancellable = listOf(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY)
        for (status in cancellable) {
            assertThat(CancelOrder(admin)(anOrder("o-$status", status), "  Store never answered ")).isEqualTo(done)
        }

        assertThat(admin.cancellations.map { it.orderId }).containsExactly("o-PLACED", "o-ACCEPTED", "o-PREPARING", "o-READY").inOrder()
        assertThat(admin.cancellations.map { it.reason }.toSet()).containsExactly("Store never answered")
    }

    @Test
    fun aPickedUpOrderIsCancelledWithTheTrimmedReason() = runTest {
        val result = CancelOrder(admin)(anOrder("o1", OrderStatus.PICKED_UP, courierId = "courier-1"), "  Courier vanished  ")

        assertThat(result).isEqualTo(done)
        assertThat(admin.cancellations).containsExactly(FakeAdminRepository.Cancellation("o1", "Courier vanished"))
    }

    @Test
    fun aPickedUpOrderStillNeedsAReason() = runTest {
        val order = anOrder("o1", OrderStatus.PICKED_UP, courierId = "courier-1")

        assertThat(CancelOrder(admin)(order, "  ")).isEqualTo(rejected(AdminRejection.REASON_REQUIRED))
        assertThat(CancelOrder(admin)(order, "x".repeat(CancelOrder.MAX_REASON_LENGTH + 1))).isEqualTo(rejected(AdminRejection.REASON_TOO_LONG))
        assertThat(admin.cancellations).isEmpty()
    }

    @Test
    fun aClaimedOrFinishedOrderIsNotCancellable() = runTest {
        val others = OrderStatus.entries - setOf(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.PICKED_UP)
        assertThat(others).containsExactly(OrderStatus.CLAIMED, OrderStatus.DELIVERED, OrderStatus.REJECTED, OrderStatus.CANCELLED)
        for (status in others) {
            assertThat(CancelOrder(admin)(anOrder("o-$status", status), "Reason")).isEqualTo(rejected(AdminRejection.ORDER_NOT_CANCELLABLE))
        }
        assertThat(admin.cancellations).isEmpty()
    }

    @Test
    fun aCancellationNeedsAReason() = runTest {
        for (reason in listOf("", "   ")) {
            assertThat(CancelOrder(admin)(anOrder("o1", OrderStatus.READY), reason)).isEqualTo(rejected(AdminRejection.REASON_REQUIRED))
        }
        assertThat(admin.cancellations).isEmpty()
    }

    @Test
    fun theReasonStopsAtTheLimitTheRulesEnforce() = runTest {
        val order = anOrder("o1", OrderStatus.READY)

        assertThat(CancelOrder(admin)(order, "x".repeat(CancelOrder.MAX_REASON_LENGTH))).isEqualTo(done)
        assertThat(CancelOrder(admin)(order, "x".repeat(CancelOrder.MAX_REASON_LENGTH + 1))).isEqualTo(rejected(AdminRejection.REASON_TOO_LONG))
        assertThat(admin.cancellations).hasSize(1)
    }

    @Test
    fun aFailedCancellationIsReportedAsFailed() = runTest {
        admin.writeFailure = IOException("offline")

        assertThat(CancelOrder(admin)(anOrder("o1", OrderStatus.READY), "Reason")).isEqualTo(AdminActionResult.Failed)
    }
}
