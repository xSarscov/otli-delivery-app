package com.otli.app.catalog.adapters.ui

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.otli.app.catalog.application.Storefront
import com.otli.app.core.testing.MainDispatcherRule
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test

class StorefrontViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val categories = listOf(aCategory("c1", "Platos", 1), aCategory("c2", "Bebidas", 2))
    private val products = listOf(
        aProduct("p1", "c1", "Nacatamal", 12000),
        aProduct("p2", "c1", "Vigoron", 8000, isAvailable = false),
        aProduct("p3", "c2", "Fresco", 2500),
    )
    private val storefront = MutableStateFlow<Storefront?>(Storefront(aMerchant("m1", isOpen = true), categories, products))

    private fun viewModel(merchantId: String? = "m1"): StorefrontViewModel {
        catalog.storefront = storefront
        val handle = SavedStateHandle(if (merchantId == null) emptyMap() else mapOf("merchantId" to merchantId))
        return StorefrontViewModel(catalog, handle)
    }

    private fun StorefrontUiState.item(productId: String) =
        sections.flatMap { it.items }.first { it.product.id == productId }

    @Test
    fun groupsProductsUnderTheirCategoriesInCategoryOrder() {
        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.merchant?.name).isEqualTo("Comedor Marta")
        assertThat(state.sections.map { it.category.name }).containsExactly("Platos", "Bebidas").inOrder()
        assertThat(state.sections[0].items.map { it.product.name }).containsExactly("Nacatamal", "Vigoron").inOrder()
        assertThat(state.sections[1].items.map { it.product.name }).containsExactly("Fresco")
    }

    @Test
    fun asksTheRepositoryForTheMerchantFromTheNavigationArguments() {
        viewModel("m7")

        assertThat(catalog.storefrontRequests).containsExactly("m7")
    }

    @Test
    fun anOpenStoreLetsAvailableProductsBeAddedToTheCart() {
        val state = viewModel().uiState.value

        assertThat(state.isClosed).isFalse()
        assertThat(state.item("p1").canAddToCart).isTrue()
        assertThat(state.item("p3").canAddToCart).isTrue()
    }

    @Test
    fun anUnavailableProductStaysVisibleButCannotBeAddedEvenWhenTheStoreIsOpen() {
        val state = viewModel().uiState.value

        assertThat(state.item("p2").product.isAvailable).isFalse()
        assertThat(state.item("p2").canAddToCart).isFalse()
    }

    @Test
    fun aClosedStoreBlocksAddingEveryProduct() {
        storefront.value = Storefront(aMerchant("m1", isOpen = false), categories, products)

        val state = viewModel().uiState.value

        assertThat(state.isClosed).isTrue()
        assertThat(state.sections.flatMap { it.items }).hasSize(3)
        assertThat(state.sections.flatMap { it.items }.map { it.canAddToCart }).containsExactly(false, false, false)
    }

    @Test
    fun theStoreClosingLiveFlipsEveryAddFlag() {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.item("p1").canAddToCart).isTrue()

        storefront.value = Storefront(aMerchant("m1", isOpen = false), categories, products)

        assertThat(viewModel.uiState.value.isClosed).isTrue()
        assertThat(viewModel.uiState.value.item("p1").canAddToCart).isFalse()
    }

    @Test
    fun categoriesWithoutProductsAndProductsWithoutACategoryAreNotShown() {
        storefront.value = Storefront(
            aMerchant("m1"),
            categories + aCategory("c3", "Postres", 3),
            products + aProduct("p9", "gone", "Huerfano"),
        )

        val state = viewModel().uiState.value

        assertThat(state.sections.map { it.category.name }).containsExactly("Platos", "Bebidas").inOrder()
        assertThat(state.sections.flatMap { it.items }.map { it.product.id }).doesNotContain("p9")
    }

    @Test
    fun staysLoadingUntilTheFirstSnapshotArrives() {
        catalog.storefront = flow { awaitCancellation() }

        val state = StorefrontViewModel(catalog, SavedStateHandle(mapOf("merchantId" to "m1"))).uiState.value

        assertThat(state.isLoading).isTrue()
    }

    @Test
    fun aMissingMerchantIsReportedAsNotFound() {
        storefront.value = null

        val state = viewModel().uiState.value

        assertThat(state.notFound).isTrue()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun aMissingMerchantIdIsNotFoundWithoutAskingTheRepository() {
        val state = viewModel(merchantId = null).uiState.value

        assertThat(state.notFound).isTrue()
        assertThat(catalog.storefrontRequests).isEmpty()
    }

    @Test
    fun aFailingListenerSurfacesAsALoadFailure() {
        catalog.storefront = flow { error("permission denied") }

        val state = StorefrontViewModel(catalog, SavedStateHandle(mapOf("merchantId" to "m1"))).uiState.value

        assertThat(state.loadFailed).isTrue()
        assertThat(state.isLoading).isFalse()
    }
}
