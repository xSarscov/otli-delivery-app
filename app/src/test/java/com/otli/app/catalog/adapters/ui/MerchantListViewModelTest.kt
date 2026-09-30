package com.otli.app.catalog.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.testing.MainDispatcherRule
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test

class MerchantListViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val merchants = FakeMerchantRepository()
    private val list = MutableStateFlow(
        listOf(aMerchant("m1", "Comedor Marta", isOpen = true), aMerchant("m2", "Pulperia Sol", isOpen = false)),
    )

    private fun viewModel(): MerchantListViewModel {
        merchants.merchantsList = list
        return MerchantListViewModel(merchants)
    }

    @Test
    fun listsEveryMerchantWithItsOpenState() {
        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.merchants.map { it.name }).containsExactly("Comedor Marta", "Pulperia Sol").inOrder()
        assertThat(state.merchants.map { it.isOpen }).containsExactly(true, false).inOrder()
    }

    @Test
    fun staysLoadingUntilTheFirstSnapshotArrives() {
        merchants.merchantsList = flow { awaitCancellation() }

        assertThat(MerchantListViewModel(merchants).uiState.value.isLoading).isTrue()
    }

    @Test
    fun anEmptyListIsNotLoadingAndNotAFailure() {
        list.value = emptyList()

        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.loadFailed).isFalse()
        assertThat(state.merchants).isEmpty()
    }

    @Test
    fun liveChangesReachTheState() {
        val viewModel = viewModel()

        list.value = list.value.map { if (it.id == "m2") it.copy(isOpen = true) else it } + aMerchant("m3", "Cafe Luz")

        assertThat(viewModel.uiState.value.merchants.map { it.id }).containsExactly("m1", "m2", "m3").inOrder()
        assertThat(viewModel.uiState.value.merchants.first { it.id == "m2" }.isOpen).isTrue()
    }

    @Test
    fun aFailingListenerSurfacesAsALoadFailureInsteadOfCrashing() {
        merchants.merchantsList = flow { error("permission denied") }

        val state = MerchantListViewModel(merchants).uiState.value

        assertThat(state.loadFailed).isTrue()
        assertThat(state.isLoading).isFalse()
    }
}
