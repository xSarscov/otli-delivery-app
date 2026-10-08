package com.otli.app.admin.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.admin.application.ApproveAccount
import com.otli.app.admin.application.FakeAdminRepository
import com.otli.app.admin.application.SuspendAccount
import com.otli.app.admin.application.anAccount
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import com.otli.app.core.testing.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class ApprovalsViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val admin = FakeAdminRepository()

    private fun viewModel() = ApprovalsViewModel(admin, ApproveAccount(admin), SuspendAccount(admin))

    private val pendingMerchant = anAccount("m-new", Role.MERCHANT, AccountStatus.PENDING, "Nuevo Comercio")
    private val pendingCourier = anAccount("c-new", Role.COURIER, AccountStatus.PENDING, "Nuevo Repartidor")
    private val activeMerchant = anAccount("m1", Role.MERCHANT, AccountStatus.ACTIVE, "Comedor Marta")
    private val suspendedCourier = anAccount("c1", Role.COURIER, AccountStatus.SUSPENDED, "Luis Mendoza")

    // --- the lists ---

    @Test
    fun itIsLoadingUntilTheFirstListArrives() {
        val gate = CompletableDeferred<Unit>()
        admin.beforeFirstList = { gate.await() }

        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.isLoading).isTrue()
        gate.complete(Unit)
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun accountsAreGroupedByStatusAndEachGroupIsSortedByNameWhateverTheArrivalOrder() {
        admin.managedAccounts.value = listOf(
            anAccount("c2", Role.COURIER, AccountStatus.PENDING, "zeta"),
            activeMerchant,
            pendingMerchant,
            anAccount("c3", Role.COURIER, AccountStatus.PENDING, "alfa"),
            suspendedCourier,
            anAccount("m2", Role.MERCHANT, AccountStatus.ACTIVE, "Bodega Ana"),
            anAccount("m0", Role.MERCHANT, AccountStatus.ACTIVE, "Bodega Ana"),
        )

        val state = viewModel().uiState.value

        assertThat(state.pending.map { it.uid }).containsExactly("c3", "m-new", "c2").inOrder()
        assertThat(state.active.map { it.uid }).containsExactly("m0", "m2", "m1").inOrder()
        assertThat(state.suspended.map { it.uid }).containsExactly("c1")
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun theListsFollowTheLiveData() {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.pending).isEmpty()

        admin.managedAccounts.value = listOf(pendingMerchant)
        assertThat(viewModel.uiState.value.pending).containsExactly(pendingMerchant)

        admin.managedAccounts.value = listOf(pendingMerchant.copy(status = AccountStatus.ACTIVE))
        assertThat(viewModel.uiState.value.pending).isEmpty()
        assertThat(viewModel.uiState.value.active.map { it.uid }).containsExactly("m-new")
    }

    @Test
    fun aRejectedListenerIsReportedInsteadOfCrashing() {
        admin.listenerError = IOException("denied")

        val state = viewModel().uiState.value

        assertThat(state.loadFailed).isTrue()
        assertThat(state.isLoading).isFalse()
    }

    // --- approving, reactivating, suspending ---

    @Test
    fun approvingAPendingMerchantOrCourierActivatesIt() {
        val viewModel = viewModel()

        viewModel.approve(pendingMerchant)
        viewModel.approve(pendingCourier)

        assertThat(admin.statusChanges).containsExactly(
            FakeAdminRepository.StatusChange("m-new", Role.MERCHANT, AccountStatus.ACTIVE),
            FakeAdminRepository.StatusChange("c-new", Role.COURIER, AccountStatus.ACTIVE),
        ).inOrder()
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.busyUid).isNull()
    }

    @Test
    fun reactivatingASuspendedAccountActivatesIt() {
        viewModel().approve(suspendedCourier)

        assertThat(admin.statusChanges).containsExactly(FakeAdminRepository.StatusChange("c1", Role.COURIER, AccountStatus.ACTIVE))
    }

    @Test
    fun suspendingAnActiveAccountSuspendsIt() {
        viewModel().suspend(activeMerchant)

        assertThat(admin.statusChanges).containsExactly(FakeAdminRepository.StatusChange("m1", Role.MERCHANT, AccountStatus.SUSPENDED))
    }

    @Test
    fun anAccountThatIsNotInTheRightStatusIsRefusedWithoutWriting() {
        val viewModel = viewModel()

        viewModel.approve(activeMerchant)
        assertThat(viewModel.uiState.value.error).isEqualTo(ApprovalError.ACTION_FAILED)
        viewModel.dismissError()
        assertThat(viewModel.uiState.value.error).isNull()
        viewModel.suspend(pendingMerchant)

        assertThat(viewModel.uiState.value.error).isEqualTo(ApprovalError.ACTION_FAILED)
        assertThat(admin.statusChanges).isEmpty()
    }

    @Test
    fun aFailedWriteIsReportedAndFreesTheScreenForTheNextAction() {
        admin.writeFailure = IOException("offline")
        val viewModel = viewModel()

        viewModel.approve(pendingMerchant)

        assertThat(viewModel.uiState.value.error).isEqualTo(ApprovalError.ACTION_FAILED)
        assertThat(viewModel.uiState.value.busyUid).isNull()

        admin.writeFailure = null
        viewModel.dismissError()
        viewModel.approve(pendingMerchant)

        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(admin.statusChanges).hasSize(1)
    }

    @Test
    fun anActionWhileAnotherIsRunningIsIgnoredAndTheBusyAccountIsMarked() {
        val gate = CompletableDeferred<Unit>()
        admin.beforeWrite = { gate.await() }
        val viewModel = viewModel()

        viewModel.approve(pendingMerchant)
        viewModel.approve(pendingCourier)

        assertThat(viewModel.uiState.value.busyUid).isEqualTo("m-new")
        gate.complete(Unit)
        assertThat(admin.statusChanges.map { it.uid }).containsExactly("m-new")
        assertThat(viewModel.uiState.value.busyUid).isNull()
    }

    @Test
    fun anOldErrorClearsWhenTheNextActionStarts() {
        admin.writeFailure = IOException("offline")
        val viewModel = viewModel()
        viewModel.approve(pendingMerchant)
        assertThat(viewModel.uiState.value.error).isNotNull()

        admin.writeFailure = null
        val gate = CompletableDeferred<Unit>()
        admin.beforeWrite = { gate.await() }
        viewModel.approve(pendingMerchant)

        assertThat(viewModel.uiState.value.error).isNull()
        gate.complete(Unit)
        assertThat(viewModel.uiState.value.error).isNull()
    }
}
