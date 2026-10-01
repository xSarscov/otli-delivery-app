package com.otli.app.ordering.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.FakeSignedInAuth
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.Actor
import com.otli.app.ordering.domain.OrderStatus
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class MerchantOrderBoardViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val orders = FakeOrderRepository()
    private val auth = FakeSignedInAuth("m1")

    private fun viewModel() = MerchantOrderBoardViewModel(orders, auth)

    private fun transition(id: String, to: OrderStatus, reason: String? = null) =
        FakeOrderRepository.Transition(id, to, Actor.MERCHANT, reason)

    // --- the board ---

    @Test
    fun itIsLoadingUntilTheFirstListArrives() {
        val viewModel = MerchantOrderBoardViewModel(orders, FakeSignedInAuth(null))

        assertThat(viewModel.uiState.value.isLoading).isTrue()
    }

    @Test
    fun placedOrdersAreIncomingAndOrdersInTheKitchenOrOnTheRoadAreInProgress() {
        orders.orders.value = listOf(
            anOrder("o1", OrderStatus.PLACED),
            anOrder("o2", OrderStatus.ACCEPTED),
            anOrder("o3", OrderStatus.PREPARING),
            anOrder("o4", OrderStatus.READY),
            anOrder("o5", OrderStatus.CLAIMED),
            anOrder("o6", OrderStatus.PICKED_UP),
            anOrder("o7", OrderStatus.DELIVERED),
            anOrder("o8", OrderStatus.REJECTED),
            anOrder("o9", OrderStatus.CANCELLED),
            anOrder("other", OrderStatus.PLACED, merchantId = "m2"),
        )

        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.incoming.map { it.id }).containsExactly("o1")
        assertThat(state.inProgress.map { it.id }).containsExactly("o2", "o3", "o4", "o5", "o6")
    }

    @Test
    fun theOldestWaitingOrderComesFirst() {
        orders.orders.value = listOf(
            anOrder("late", OrderStatus.PLACED, createdAtMillis = 3_000L),
            anOrder("early", OrderStatus.PLACED, createdAtMillis = 1_000L),
            anOrder("middle", OrderStatus.PLACED, createdAtMillis = 2_000L),
        )

        assertThat(viewModel().uiState.value.incoming.map { it.id }).containsExactly("early", "middle", "late").inOrder()
    }

    @Test
    fun theBoardFollowsTheLiveList() {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.incoming).isEmpty()

        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        assertThat(viewModel.uiState.value.incoming.map { it.id }).containsExactly("o1")

        orders.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED))
        assertThat(viewModel.uiState.value.incoming).isEmpty()
        assertThat(viewModel.uiState.value.inProgress.map { it.id }).containsExactly("o1")
    }

    @Test
    fun aFailingListenerShowsALoadErrorInsteadOfCrashing() {
        orders.listenerError = IllegalStateException("PERMISSION_DENIED")

        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isEqualTo(OrderBoardError.LOAD_FAILED)
    }

    // --- accept and the kitchen steps ---

    @Test
    fun acceptingAPlacedOrderMovesItToAccepted() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))

        viewModel().advance("o1")

        assertThat(orders.transitions).containsExactly(transition("o1", OrderStatus.ACCEPTED))
    }

    @Test
    fun theKitchenStepsGoAcceptedToPreparingToReady() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED), anOrder("o2", OrderStatus.PREPARING))

        val viewModel = viewModel()
        viewModel.advance("o1")
        viewModel.advance("o2")

        assertThat(orders.transitions).containsExactly(
            transition("o1", OrderStatus.PREPARING),
            transition("o2", OrderStatus.READY),
        ).inOrder()
    }

    @Test
    fun anOrderTheMerchantCannotAdvanceIsLeftAlone() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.READY), anOrder("o2", OrderStatus.CLAIMED))

        val viewModel = viewModel()
        viewModel.advance("o1")
        viewModel.advance("o2")
        viewModel.advance("unknown")

        assertThat(orders.transitions).isEmpty()
    }

    @Test
    fun aSecondTapWhileTheStepIsInFlightIsIgnoredAndTheOrderIsBusyMeanwhile() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        val release = CompletableDeferred<Unit>()
        orders.transitionOutcome = {
            release.await()
            Result.success(Unit)
        }
        val viewModel = viewModel()

        viewModel.advance("o1")
        viewModel.advance("o1")
        assertThat(viewModel.uiState.value.busy).containsExactly("o1")

        release.complete(Unit)
        assertThat(orders.transitions).hasSize(1)
        assertThat(viewModel.uiState.value.busy).isEmpty()
    }

    @Test
    fun aRefusedStepShowsAnErrorThatCanBeDismissed() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        orders.transitionOutcome = { Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()

        viewModel.advance("o1")

        assertThat(viewModel.uiState.value.error).isEqualTo(OrderBoardError.ACTION_FAILED)
        assertThat(viewModel.uiState.value.busy).isEmpty()

        viewModel.dismissError()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun tryingAgainClearsThePreviousError() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        orders.transitionOutcome = { Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()
        viewModel.advance("o1")

        val release = CompletableDeferred<Unit>()
        orders.transitionOutcome = {
            release.await()
            Result.success(Unit)
        }
        viewModel.advance("o1")

        assertThat(viewModel.uiState.value.error).isNull()
    }

    // --- rejecting with a reason ---

    @Test
    fun rejectingNeedsAReasonAndNothingIsSentWithoutOne() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        val viewModel = viewModel()

        viewModel.startReject("o1")
        assertThat(viewModel.uiState.value.rejecting).isEqualTo("o1")

        viewModel.confirmReject()
        viewModel.setRejectReason("   ")
        viewModel.confirmReject()

        assertThat(orders.transitions).isEmpty()
        assertThat(viewModel.uiState.value.rejectReasonMissing).isTrue()
        assertThat(viewModel.uiState.value.rejecting).isEqualTo("o1")
    }

    @Test
    fun typingAReasonClearsTheMissingReasonMessage() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        val viewModel = viewModel()
        viewModel.startReject("o1")
        viewModel.confirmReject()

        viewModel.setRejectReason("N")

        assertThat(viewModel.uiState.value.rejectReasonMissing).isFalse()
    }

    @Test
    fun aReasonIsTrimmedSentAndClosesTheDialog() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        val viewModel = viewModel()
        viewModel.startReject("o1")
        viewModel.setRejectReason("  No hay nacatamales  ")

        viewModel.confirmReject()

        assertThat(orders.transitions).containsExactly(transition("o1", OrderStatus.REJECTED, "No hay nacatamales"))
        assertThat(viewModel.uiState.value.rejecting).isNull()
        assertThat(viewModel.uiState.value.rejectReason).isEmpty()
    }

    @Test
    fun aRefusedRejectionKeepsTheDialogOpenWithTheReasonAndShowsAnError() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        orders.transitionOutcome = { Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()
        viewModel.startReject("o1")
        viewModel.setRejectReason("No hay")

        viewModel.confirmReject()

        assertThat(viewModel.uiState.value.rejecting).isEqualTo("o1")
        assertThat(viewModel.uiState.value.rejectReason).isEqualTo("No hay")
        assertThat(viewModel.uiState.value.error).isEqualTo(OrderBoardError.ACTION_FAILED)
    }

    @Test
    fun dismissingTheDialogForgetsTheReason() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        val viewModel = viewModel()
        viewModel.startReject("o1")
        viewModel.setRejectReason("No hay")

        viewModel.dismissReject()

        assertThat(viewModel.uiState.value.rejecting).isNull()
        assertThat(viewModel.uiState.value.rejectReason).isEmpty()
        assertThat(viewModel.uiState.value.rejectReasonMissing).isFalse()
    }

    @Test
    fun onlyAPlacedOrderCanBeRejected() {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED))
        val viewModel = viewModel()

        viewModel.startReject("o1")
        viewModel.startReject("unknown")

        assertThat(viewModel.uiState.value.rejecting).isNull()
    }

    @Test
    fun theDialogClosesAndForgetsTheReasonWhenTheOrderIsNoLongerWaiting() = ordersLeaveWhileRejecting { viewModel ->
        viewModel.setRejectReason("No hay")
    }.let { viewModel ->
        assertThat(viewModel.uiState.value.rejecting).isNull()
        assertThat(viewModel.uiState.value.rejectReason).isEmpty()
    }

    @Test
    fun theDialogForgetsAPendingMissingReasonMessageWhenTheOrderIsNoLongerWaiting() = ordersLeaveWhileRejecting { viewModel ->
        viewModel.confirmReject()
    }.let { viewModel ->
        assertThat(viewModel.uiState.value.rejectReasonMissing).isFalse()
    }

    private fun ordersLeaveWhileRejecting(typing: (MerchantOrderBoardViewModel) -> Unit): MerchantOrderBoardViewModel {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        val viewModel = viewModel()
        viewModel.startReject("o1")
        typing(viewModel)
        orders.orders.value = listOf(anOrder("o1", OrderStatus.CANCELLED))
        return viewModel
    }
}
