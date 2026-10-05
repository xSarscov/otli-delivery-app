package com.otli.app.admin.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.admin.application.CancelOrder
import com.otli.app.admin.application.FakeAdminRepository
import com.otli.app.admin.application.ReleaseClaim
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class StuckOrdersViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val admin = FakeAdminRepository()

    private fun viewModel() = StuckOrdersViewModel(admin, ReleaseClaim(admin), CancelOrder(admin))

    private val ready = anOrder("ready", OrderStatus.READY, createdAtMillis = 3_000)
    private val claimed = anOrder("claimed", OrderStatus.CLAIMED, createdAtMillis = 2_000, courierId = "courier-1")
    private val placed = anOrder("placed", OrderStatus.PLACED, createdAtMillis = 1_000)

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
    fun ordersAreGroupedByWhoHoldsThemAndEachGroupIsNewestFirstWhateverTheArrivalOrder() {
        admin.stuckOrders.value = listOf(
            anOrder("k-old", OrderStatus.PLACED, createdAtMillis = 100),
            anOrder("w-old", OrderStatus.READY, createdAtMillis = 200),
            anOrder("c-old", OrderStatus.CLAIMED, createdAtMillis = 300, courierId = "courier-1"),
            anOrder("k-new", OrderStatus.PREPARING, createdAtMillis = 900),
            anOrder("w-new", OrderStatus.READY, createdAtMillis = 800),
            anOrder("c-new", OrderStatus.CLAIMED, createdAtMillis = 700, courierId = "courier-2"),
            anOrder("k-mid", OrderStatus.ACCEPTED, createdAtMillis = 500),
            anOrder("p-old", OrderStatus.PICKED_UP, createdAtMillis = 400, courierId = "courier-3"),
            anOrder("p-new", OrderStatus.PICKED_UP, createdAtMillis = 600, courierId = "courier-4"),
        )

        val state = viewModel().uiState.value

        assertThat(state.waiting.map { it.id }).containsExactly("w-new", "w-old").inOrder()
        assertThat(state.withCourier.map { it.id }).containsExactly("c-new", "c-old").inOrder()
        assertThat(state.inKitchen.map { it.id }).containsExactly("k-new", "k-mid", "k-old").inOrder()
        assertThat(state.pickedUp.map { it.id }).containsExactly("p-new", "p-old").inOrder()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun aFinishedOrderHasNoPlaceInTheListsAndAPickedUpOneIsOnTheWay() {
        admin.stuckOrders.value = listOf(
            ready,
            anOrder("picked", OrderStatus.PICKED_UP, courierId = "courier-1"),
            anOrder("done", OrderStatus.DELIVERED),
            anOrder("gone", OrderStatus.CANCELLED),
            anOrder("no", OrderStatus.REJECTED),
        )

        val state = viewModel().uiState.value

        assertThat(state.waiting.map { it.id }).containsExactly("ready")
        assertThat(state.withCourier).isEmpty()
        assertThat(state.inKitchen).isEmpty()
        assertThat(state.pickedUp.map { it.id }).containsExactly("picked")
    }

    @Test
    fun anOrderMovesBetweenGroupsAsItsStatusChanges() {
        admin.stuckOrders.value = listOf(ready)
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.waiting.map { it.id }).containsExactly("ready")

        admin.stuckOrders.value = listOf(ready.copy(status = OrderStatus.CLAIMED, courierId = "courier-1"))

        assertThat(viewModel.uiState.value.waiting).isEmpty()
        assertThat(viewModel.uiState.value.withCourier.map { it.id }).containsExactly("ready")
    }

    @Test
    fun aRejectedListenerIsReportedInsteadOfCrashing() {
        admin.listenerError = IOException("denied")

        val state = viewModel().uiState.value

        assertThat(state.loadFailed).isTrue()
        assertThat(state.isLoading).isFalse()
    }

    // --- releasing ---

    @Test
    fun releasingAClaimedOrderWritesTheRelease() {
        val viewModel = viewModel()

        viewModel.release(claimed)

        assertThat(admin.releases).containsExactly("claimed")
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.busyOrderId).isNull()
    }

    @Test
    fun anOrderThatIsNotClaimedIsNotReleasedAndTheErrorIsShown() {
        val viewModel = viewModel()

        viewModel.release(ready)

        assertThat(admin.releases).isEmpty()
        assertThat(viewModel.uiState.value.error).isEqualTo(StuckOrdersError.ACTION_FAILED)
    }

    @Test
    fun aFailedReleaseIsReportedAndFreesTheScreen() {
        admin.writeFailure = IOException("offline")
        val viewModel = viewModel()

        viewModel.release(claimed)

        assertThat(viewModel.uiState.value.error).isEqualTo(StuckOrdersError.ACTION_FAILED)
        assertThat(viewModel.uiState.value.busyOrderId).isNull()
        viewModel.dismissError()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun anActionWhileAnotherRunsIsIgnoredAndTheBusyOrderIsMarked() {
        val gate = CompletableDeferred<Unit>()
        admin.beforeWrite = { gate.await() }
        val viewModel = viewModel()

        viewModel.release(claimed)
        viewModel.release(claimed.copy(id = "claimed-2"))

        assertThat(viewModel.uiState.value.busyOrderId).isEqualTo("claimed")
        gate.complete(Unit)
        assertThat(admin.releases).containsExactly("claimed")
        assertThat(viewModel.uiState.value.busyOrderId).isNull()
    }

    @Test
    fun anOldErrorClearsWhenTheNextActionStarts() {
        admin.writeFailure = IOException("offline")
        val viewModel = viewModel()
        viewModel.release(claimed)
        assertThat(viewModel.uiState.value.error).isNotNull()

        admin.writeFailure = null
        val gate = CompletableDeferred<Unit>()
        admin.beforeWrite = { gate.await() }
        viewModel.release(claimed)

        assertThat(viewModel.uiState.value.error).isNull()
        gate.complete(Unit)
    }

    // --- cancelling ---

    @Test
    fun startingACancellationOpensTheReasonDialogForThatOrderAndDismissingClosesIt() {
        admin.stuckOrders.value = listOf(ready)
        val viewModel = viewModel()

        viewModel.startCancel(ready)
        assertThat(viewModel.uiState.value.cancelTarget?.id).isEqualTo("ready")

        viewModel.dismissCancel()
        assertThat(viewModel.uiState.value.cancelTarget).isNull()
    }

    @Test
    fun confirmingWithAReasonCancelsTheOrderWithTheTrimmedReasonAndClosesTheDialog() {
        admin.stuckOrders.value = listOf(ready, placed)
        val viewModel = viewModel()

        viewModel.startCancel(placed)
        viewModel.confirmCancel("  Store never answered ")

        assertThat(admin.cancellations).containsExactly(FakeAdminRepository.Cancellation("placed", "Store never answered"))
        assertThat(viewModel.uiState.value.cancelTarget).isNull()
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.busyOrderId).isNull()
    }

    @Test
    fun aPickedUpOrderIsCancelledWithTheTrimmedReasonAndLeavesTheListWhenTheCourierSlotIsFreed() {
        val picked = anOrder("picked", OrderStatus.PICKED_UP, createdAtMillis = 500, courierId = "courier-1")
        admin.stuckOrders.value = listOf(ready, picked)
        val viewModel = viewModel()

        viewModel.startCancel(picked)
        viewModel.confirmCancel("  Courier vanished after pickup ")

        assertThat(admin.cancellations).containsExactly(FakeAdminRepository.Cancellation("picked", "Courier vanished after pickup"))
        assertThat(viewModel.uiState.value.cancelTarget).isNull()
        assertThat(viewModel.uiState.value.error).isNull()

        admin.stuckOrders.value = listOf(ready)
        assertThat(viewModel.uiState.value.pickedUp).isEmpty()
        assertThat(viewModel.uiState.value.waiting.map { it.id }).containsExactly("ready")
    }

    @Test
    fun aPickedUpOrderDeliveredWhileTheDialogWasOpenIsNotCancelled() {
        val picked = anOrder("picked", OrderStatus.PICKED_UP, courierId = "courier-1")
        admin.stuckOrders.value = listOf(picked)
        val viewModel = viewModel()
        viewModel.startCancel(picked)

        admin.stuckOrders.value = listOf(picked.copy(status = OrderStatus.DELIVERED))
        viewModel.confirmCancel("Reason")

        assertThat(admin.cancellations).isEmpty()
        assertThat(viewModel.uiState.value.error).isEqualTo(StuckOrdersError.ACTION_FAILED)
    }

    @Test
    fun aBlankOrTooLongReasonKeepsTheDialogOpenAndWritesNothing() {
        admin.stuckOrders.value = listOf(ready)
        val viewModel = viewModel()
        viewModel.startCancel(ready)

        viewModel.confirmCancel("   ")
        assertThat(viewModel.uiState.value.reasonInvalid).isTrue()
        assertThat(viewModel.uiState.value.cancelTarget?.id).isEqualTo("ready")

        viewModel.confirmCancel("x".repeat(CancelOrder.MAX_REASON_LENGTH + 1))
        assertThat(viewModel.uiState.value.reasonInvalid).isTrue()
        assertThat(admin.cancellations).isEmpty()

        viewModel.confirmCancel("Closed early")
        assertThat(admin.cancellations).hasSize(1)
        assertThat(viewModel.uiState.value.reasonInvalid).isFalse()
    }

    @Test
    fun reopeningTheDialogStartsWithoutTheOldReasonComplaint() {
        admin.stuckOrders.value = listOf(ready)
        val viewModel = viewModel()
        viewModel.startCancel(ready)
        viewModel.confirmCancel("")

        assertThat(viewModel.uiState.value.reasonInvalid).isTrue()
        viewModel.dismissCancel()
        assertThat(viewModel.uiState.value.reasonInvalid).isFalse()

        viewModel.startCancel(ready)
        viewModel.confirmCancel("")
        viewModel.startCancel(ready)
        assertThat(viewModel.uiState.value.reasonInvalid).isFalse()
    }

    @Test
    fun startingACancellationClearsAnOldError() {
        admin.writeFailure = IOException("offline")
        val viewModel = viewModel()
        viewModel.release(claimed)
        assertThat(viewModel.uiState.value.error).isNotNull()

        viewModel.startCancel(ready)

        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun aFailedCancellationClosesTheDialogAndShowsTheError() {
        admin.stuckOrders.value = listOf(ready)
        admin.writeFailure = IOException("offline")
        val viewModel = viewModel()
        viewModel.startCancel(ready)

        viewModel.confirmCancel("Reason")

        assertThat(viewModel.uiState.value.cancelTarget).isNull()
        assertThat(viewModel.uiState.value.error).isEqualTo(StuckOrdersError.ACTION_FAILED)
        assertThat(viewModel.uiState.value.busyOrderId).isNull()
    }

    @Test
    fun theOrderIsCheckedAgainstItsLatestStatusWhenTheReasonIsConfirmed() {
        admin.stuckOrders.value = listOf(ready)
        val viewModel = viewModel()
        viewModel.startCancel(ready)

        admin.stuckOrders.value = listOf(ready.copy(status = OrderStatus.CLAIMED, courierId = "courier-1"))
        viewModel.confirmCancel("Reason")

        assertThat(admin.cancellations).isEmpty()
        assertThat(viewModel.uiState.value.error).isEqualTo(StuckOrdersError.ACTION_FAILED)
        assertThat(viewModel.uiState.value.cancelTarget).isNull()
    }

    @Test
    fun anOrderThatLeftTheListsWhileTheDialogWasOpenIsNotCancelled() {
        admin.stuckOrders.value = listOf(ready)
        val viewModel = viewModel()
        viewModel.startCancel(ready)

        admin.stuckOrders.value = emptyList()
        viewModel.confirmCancel("Reason")

        assertThat(admin.cancellations).isEmpty()
        assertThat(viewModel.uiState.value.error).isEqualTo(StuckOrdersError.ACTION_FAILED)
    }

    @Test
    fun confirmingWithoutAnOpenDialogDoesNothing() {
        admin.stuckOrders.value = listOf(ready)
        val viewModel = viewModel()

        viewModel.confirmCancel("Reason")

        assertThat(admin.cancellations).isEmpty()
        assertThat(viewModel.uiState.value.error).isNull()
    }
}
