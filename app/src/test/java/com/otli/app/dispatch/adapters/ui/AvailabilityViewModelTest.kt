package com.otli.app.dispatch.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.dispatch.application.FakeDispatchRepository
import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.ordering.application.FakeSignedInAuth
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class AvailabilityViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val dispatch = FakeDispatchRepository()
    private val auth = FakeSignedInAuth("courier-1")

    private fun viewModel() = AvailabilityViewModel(dispatch, auth)

    // --- what the switch shows ---

    @Test
    fun itIsLoadingUntilTheSessionArrives() {
        val signedOut = AvailabilityViewModel(dispatch, FakeSignedInAuth(null))

        assertThat(signedOut.uiState.value.isLoading).isTrue()
        assertThat(signedOut.uiState.value.isOnline).isFalse()
    }

    @Test
    fun anOfflineCourierSeesTheSwitchOff() {
        dispatch.courier.value = CourierAvailability(isOnline = false, activeOrderId = null)

        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.isOnline).isFalse()
        assertThat(state.hasActiveOrder).isFalse()
    }

    @Test
    fun anOnlineCourierWithAnActiveOrderSeesTheSwitchOnAndLocked() {
        dispatch.courier.value = CourierAvailability(isOnline = true, activeOrderId = "o7")

        val state = viewModel().uiState.value

        assertThat(state.isOnline).isTrue()
        assertThat(state.hasActiveOrder).isTrue()
    }

    @Test
    fun aCourierWithoutADocumentIsOfflineAndNoLongerLoading() {
        dispatch.courier.value = null

        val state = viewModel().uiState.value

        assertThat(state.isOnline).isFalse()
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun theSwitchFollowsTheLiveDocument() {
        dispatch.courier.value = CourierAvailability(false, null)
        val viewModel = viewModel()

        dispatch.courier.value = CourierAvailability(true, null)
        assertThat(viewModel.uiState.value.isOnline).isTrue()

        dispatch.courier.value = CourierAvailability(true, "o1")
        assertThat(viewModel.uiState.value.hasActiveOrder).isTrue()

        dispatch.courier.value = CourierAvailability(true, null)
        assertThat(viewModel.uiState.value.hasActiveOrder).isFalse()
    }

    @Test
    fun aFailingListenerShowsALoadErrorInsteadOfCrashing() {
        dispatch.listenerError = IllegalStateException("PERMISSION_DENIED")

        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isEqualTo(AvailabilityError.LOAD_FAILED)
    }

    // --- going online and offline ---

    @Test
    fun goingOnlineAsksTheRepositoryForTheSignedInCourier() {
        dispatch.courier.value = CourierAvailability(false, null)
        val viewModel = viewModel()

        viewModel.setOnline(true)

        assertThat(dispatch.onlineRequests).containsExactly("courier-1" to true)
        assertThat(viewModel.uiState.value.isOnline).isTrue()
        assertThat(viewModel.uiState.value.isUpdating).isFalse()
    }

    @Test
    fun goingOfflineAsksTheRepository() {
        dispatch.courier.value = CourierAvailability(true, null)
        val viewModel = viewModel()

        viewModel.setOnline(false)

        assertThat(dispatch.onlineRequests).containsExactly("courier-1" to false)
        assertThat(viewModel.uiState.value.isOnline).isFalse()
    }

    @Test
    fun aCourierWithAnActiveOrderCannotGoOffline() {
        dispatch.courier.value = CourierAvailability(true, "o1")
        val viewModel = viewModel()

        viewModel.setOnline(false)

        assertThat(dispatch.onlineRequests).isEmpty()
        assertThat(viewModel.uiState.value.isOnline).isTrue()
    }

    @Test
    fun nothingIsSentWhileSignedOut() {
        val viewModel = AvailabilityViewModel(dispatch, FakeSignedInAuth(null))

        viewModel.setOnline(true)

        assertThat(dispatch.onlineRequests).isEmpty()
    }

    @Test
    fun nothingIsSentBeforeTheCourierDocumentHasArrived() {
        val arrives = CompletableDeferred<Unit>()
        dispatch.firstEmissionGate = arrives
        dispatch.courier.value = CourierAvailability(false, null)
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        viewModel.setOnline(true)
        assertThat(dispatch.onlineRequests).isEmpty()

        arrives.complete(Unit)
        viewModel.setOnline(true)
        assertThat(dispatch.onlineRequests).containsExactly("courier-1" to true)
    }

    @Test
    fun aSecondTapWhileTheChangeIsInFlightIsIgnored() {
        dispatch.courier.value = CourierAvailability(false, null)
        val release = CompletableDeferred<Unit>()
        dispatch.onlineOutcome = { _, _ ->
            release.await()
            Result.success(Unit)
        }
        val viewModel = viewModel()

        viewModel.setOnline(true)
        viewModel.setOnline(true)
        assertThat(viewModel.uiState.value.isUpdating).isTrue()

        release.complete(Unit)
        assertThat(dispatch.onlineRequests).hasSize(1)
        assertThat(viewModel.uiState.value.isUpdating).isFalse()
    }

    @Test
    fun aRefusedChangeShowsAnErrorThatCanBeDismissedAndKeepsTheSwitch() {
        dispatch.courier.value = CourierAvailability(false, null)
        dispatch.onlineOutcome = { _, _ -> Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()

        viewModel.setOnline(true)

        assertThat(viewModel.uiState.value.error).isEqualTo(AvailabilityError.UPDATE_FAILED)
        assertThat(viewModel.uiState.value.isOnline).isFalse()
        assertThat(viewModel.uiState.value.isUpdating).isFalse()

        viewModel.dismissError()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun tryingAgainClearsThePreviousError() {
        dispatch.courier.value = CourierAvailability(false, null)
        dispatch.onlineOutcome = { _, _ -> Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()
        viewModel.setOnline(true)

        val release = CompletableDeferred<Unit>()
        dispatch.onlineOutcome = { _, _ ->
            release.await()
            Result.success(Unit)
        }
        viewModel.setOnline(true)

        assertThat(viewModel.uiState.value.error).isNull()
    }
}
