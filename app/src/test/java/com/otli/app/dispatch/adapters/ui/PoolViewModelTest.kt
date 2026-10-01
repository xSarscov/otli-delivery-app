package com.otli.app.dispatch.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.dispatch.application.FakeDispatchRepository
import com.otli.app.dispatch.application.aPoolOrder
import com.otli.app.dispatch.domain.ClaimDecision
import com.otli.app.dispatch.domain.ClaimDenial
import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.dispatch.domain.PoolGate
import com.otli.app.ordering.application.FakeSignedInAuth
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class PoolViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val dispatch = FakeDispatchRepository()
    private val auth = FakeSignedInAuth("courier-1")

    private val online = CourierAvailability(isOnline = true, activeOrderId = null)

    private fun viewModel() = PoolViewModel(dispatch, auth)

    // --- who sees the pool ---

    @Test
    fun itIsLoadingUntilTheSessionArrives() {
        val signedOut = PoolViewModel(dispatch, FakeSignedInAuth(null))

        assertThat(signedOut.uiState.value.isLoading).isTrue()
    }

    @Test
    fun anOfflineCourierSeesNoOrdersEvenWhenSomeAreReady() {
        dispatch.pool.value = listOf(aPoolOrder("o1"))
        dispatch.courier = CourierAvailability(isOnline = false, activeOrderId = null)

        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.gate).isEqualTo(PoolGate.OFFLINE)
        assertThat(state.orders).isEmpty()
    }

    @Test
    fun anOnlineFreeCourierSeesTheReadyOrders() {
        dispatch.pool.value = listOf(aPoolOrder("o1"), aPoolOrder("o2"))
        dispatch.courier = online

        val state = viewModel().uiState.value

        assertThat(state.gate).isEqualTo(PoolGate.OPEN)
        assertThat(state.orders.map { it.id }).containsExactly("o1", "o2")
    }

    @Test
    fun aCourierWithAnActiveOrderSeesNoPoolAtAll() {
        dispatch.pool.value = listOf(aPoolOrder("o1"))
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o9")

        val state = viewModel().uiState.value

        assertThat(state.gate).isEqualTo(PoolGate.BUSY)
        assertThat(state.orders).isEmpty()
    }

    @Test
    fun theOrdersAreListedOldestReadyFirstAndAJustReadyOneLast() {
        dispatch.pool.value = listOf(
            aPoolOrder("late", readyAtMillis = 3_000L),
            aPoolOrder("just-ready", readyAtMillis = 0L),
            aPoolOrder("early", readyAtMillis = 1_000L),
        )
        dispatch.courier = online

        assertThat(viewModel().uiState.value.orders.map { it.id }).containsExactly("early", "late", "just-ready").inOrder()
    }

    @Test
    fun ordersReadyAtTheSameTimeAreListedByIdSoTheListIsStable() {
        dispatch.pool.value = listOf(aPoolOrder("b", readyAtMillis = 1_000L), aPoolOrder("a", readyAtMillis = 1_000L))
        dispatch.courier = online

        assertThat(viewModel().uiState.value.orders.map { it.id }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun theOrdersFollowTheLivePoolAndAClaimedOrderLeavesIt() {
        dispatch.courier = online
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.orders).isEmpty()

        dispatch.pool.value = listOf(aPoolOrder("o1"), aPoolOrder("o2", readyAtMillis = 2_000L))
        assertThat(viewModel.uiState.value.orders.map { it.id }).containsExactly("o1", "o2")

        dispatch.pool.value = listOf(aPoolOrder("o2", readyAtMillis = 2_000L))
        assertThat(viewModel.uiState.value.orders.map { it.id }).containsExactly("o2")
    }

    @Test
    fun thePoolHidesWhenTheCourierGoesOfflineAndBusyAndReappearsAfterTheDelivery() {
        dispatch.pool.value = listOf(aPoolOrder("o1"))
        dispatch.courier = online
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.orders).hasSize(1)

        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")
        assertThat(viewModel.uiState.value.gate).isEqualTo(PoolGate.BUSY)
        assertThat(viewModel.uiState.value.orders).isEmpty()

        dispatch.courier = online
        assertThat(viewModel.uiState.value.gate).isEqualTo(PoolGate.OPEN)
        assertThat(viewModel.uiState.value.orders.map { it.id }).containsExactly("o1")

        dispatch.courier = CourierAvailability(isOnline = false, activeOrderId = null)
        assertThat(viewModel.uiState.value.orders).isEmpty()
    }

    @Test
    fun anUnchangedCourierDocumentDoesNotRestartThePoolListener() {
        dispatch.pool.value = listOf(aPoolOrder("o1"))
        dispatch.courier = online
        val viewModel = viewModel()
        assertThat(dispatch.poolSubscriptions).isEqualTo(1)

        dispatch.courier = online.copy()

        assertThat(dispatch.poolSubscriptions).isEqualTo(1)
        assertThat(viewModel.uiState.value.orders.map { it.id }).containsExactly("o1")
    }

    @Test
    fun anOfflineOrBusyCourierNeverStartsThePoolListener() {
        dispatch.courier = CourierAvailability(isOnline = false, activeOrderId = null)
        viewModel()
        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o1")

        assertThat(dispatch.poolSubscriptions).isEqualTo(0)
    }

    @Test
    fun aFailingListenerShowsALoadErrorInsteadOfCrashing() {
        dispatch.listenerError = IllegalStateException("PERMISSION_DENIED")

        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.message).isEqualTo(PoolMessage.LOAD_FAILED)
    }

    // --- claiming ---

    @Test
    fun claimingAsksTheRepositoryForTheSignedInCourier() {
        dispatch.pool.value = listOf(aPoolOrder("o1"))
        dispatch.courier = online
        val viewModel = viewModel()

        viewModel.claim("o1")

        assertThat(dispatch.claims).containsExactly("o1" to "courier-1")
        assertThat(viewModel.uiState.value.message).isNull()
        assertThat(viewModel.uiState.value.claiming).isEmpty()
    }

    @Test
    fun anOrderThatIsNotInThePoolCannotBeClaimed() {
        dispatch.pool.value = listOf(aPoolOrder("o1"))
        dispatch.courier = online

        viewModel().claim("unknown")

        assertThat(dispatch.claims).isEmpty()
    }

    @Test
    fun nothingIsClaimedWhileOfflineOrBusy() {
        dispatch.pool.value = listOf(aPoolOrder("o1"))
        dispatch.courier = CourierAvailability(isOnline = false, activeOrderId = null)
        val viewModel = viewModel()
        viewModel.claim("o1")

        dispatch.courier = CourierAvailability(isOnline = true, activeOrderId = "o9")
        viewModel.claim("o1")

        assertThat(dispatch.claims).isEmpty()
    }

    @Test
    fun aSecondTapWhileTheClaimIsInFlightIsIgnoredAndTheOrderIsClaimingMeanwhile() {
        dispatch.pool.value = listOf(aPoolOrder("o1"))
        dispatch.courier = online
        val release = CompletableDeferred<Unit>()
        dispatch.claimOutcome = { _, _ ->
            release.await()
            Result.success(ClaimDecision.Allowed)
        }
        val viewModel = viewModel()

        viewModel.claim("o1")
        viewModel.claim("o1")
        assertThat(viewModel.uiState.value.claiming).containsExactly("o1")

        release.complete(Unit)
        assertThat(dispatch.claims).hasSize(1)
        assertThat(viewModel.uiState.value.claiming).isEmpty()
    }

    @Test
    fun losingTheRaceTellsTheCourierTheOrderWasTaken() {
        dispatch.pool.value = listOf(aPoolOrder("o1"))
        dispatch.courier = online
        dispatch.claimOutcome = { _, _ -> Result.success(ClaimDecision.Denied(ClaimDenial.ALREADY_CLAIMED)) }
        val viewModel = viewModel()

        viewModel.claim("o1")

        assertThat(viewModel.uiState.value.message).isEqualTo(PoolMessage.ALREADY_TAKEN)
    }

    @Test
    fun everyDenialHasItsOwnMessage() {
        val expected = mapOf(
            ClaimDenial.NOT_READY to PoolMessage.NOT_READY,
            ClaimDenial.ALREADY_CLAIMED to PoolMessage.ALREADY_TAKEN,
            ClaimDenial.COURIER_BUSY to PoolMessage.BUSY,
            ClaimDenial.COURIER_OFFLINE to PoolMessage.OFFLINE,
            ClaimDenial.COURIER_NOT_ACTIVE to PoolMessage.NOT_ACTIVE,
        )
        assertThat(expected.keys).containsExactlyElementsIn(ClaimDenial.entries)
        expected.forEach { (denial, message) ->
            val repository = FakeDispatchRepository()
            repository.pool.value = listOf(aPoolOrder("o1"))
            repository.courier = online
            repository.claimOutcome = { _, _ -> Result.success(ClaimDecision.Denied(denial)) }
            val viewModel = PoolViewModel(repository, auth)

            viewModel.claim("o1")

            assertThat(viewModel.uiState.value.message).isEqualTo(message)
        }
    }

    @Test
    fun aTransportFailureIsAClaimFailureNotACrash() {
        dispatch.pool.value = listOf(aPoolOrder("o1"))
        dispatch.courier = online
        dispatch.claimOutcome = { _, _ -> Result.failure(IllegalStateException("offline")) }
        val viewModel = viewModel()

        viewModel.claim("o1")

        assertThat(viewModel.uiState.value.message).isEqualTo(PoolMessage.CLAIM_FAILED)
        assertThat(viewModel.uiState.value.claiming).isEmpty()
    }

    @Test
    fun theMessageCanBeDismissedAndAnotherAttemptClearsIt() {
        dispatch.pool.value = listOf(aPoolOrder("o1"))
        dispatch.courier = online
        dispatch.claimOutcome = { _, _ -> Result.success(ClaimDecision.Denied(ClaimDenial.ALREADY_CLAIMED)) }
        val viewModel = viewModel()
        viewModel.claim("o1")
        viewModel.dismissMessage()
        assertThat(viewModel.uiState.value.message).isNull()

        viewModel.claim("o1")
        assertThat(viewModel.uiState.value.message).isEqualTo(PoolMessage.ALREADY_TAKEN)

        val release = CompletableDeferred<Unit>()
        dispatch.claimOutcome = { _, _ ->
            release.await()
            Result.success(ClaimDecision.Allowed)
        }
        viewModel.claim("o1")
        assertThat(viewModel.uiState.value.message).isNull()
    }
}
