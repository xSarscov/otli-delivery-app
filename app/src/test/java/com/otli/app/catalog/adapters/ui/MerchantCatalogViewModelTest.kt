package com.otli.app.catalog.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.adapters.ui.RecordingAuthRepository
import com.otli.app.auth.domain.AuthUser
import com.otli.app.catalog.domain.Category
import com.otli.app.core.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test

class MerchantCatalogViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val auth = RecordingAuthRepository().apply { authState.value = AuthUser("m1", "marta@otli.test") }

    private fun viewModel() = MerchantCatalogViewModel(catalog, auth)

    @Test
    fun showsTheSignedInMerchantsCategoriesAndProducts() {
        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.categories.map { it.name }).containsExactly("Platos", "Bebidas").inOrder()
        assertThat(state.products.map { it.name }).containsExactly("Nacatamal", "Fresco")
    }

    @Test
    fun staysLoadingUntilThereIsASignedInUser() {
        auth.authState.value = null

        assertThat(viewModel().uiState.value.isLoading).isTrue()
    }

    @Test
    fun liveCatalogChangesReachTheState() {
        val viewModel = viewModel()

        catalog.products.value = catalog.products.value + aProduct("p3", "c1", "Gallo pinto", 9000)

        assertThat(viewModel.uiState.value.products.map { it.id }).contains("p3")
    }

    @Test
    fun availabilityToggleWritesTheChoiceAndIsReflected() {
        val viewModel = viewModel()

        viewModel.onAvailabilityChange("p2", true)

        assertThat(catalog.availabilityChanges).containsExactly("p2" to true)
        assertThat(viewModel.uiState.value.products.first { it.id == "p2" }.isAvailable).isTrue()
    }

    @Test
    fun aFailedAvailabilityToggleShowsAnError() {
        catalog.writeOutcome = { Result.failure(IllegalStateException("offline")) }
        val viewModel = viewModel()

        viewModel.onAvailabilityChange("p1", false)

        assertThat(viewModel.uiState.value.error).isEqualTo(MerchantCatalogError.AVAILABILITY_FAILED)
        assertThat(viewModel.uiState.value.products.first { it.id == "p1" }.isAvailable).isTrue()
    }

    @Test
    fun aNewCategoryGetsTheNextSortOrderAndTrimmedName() {
        val viewModel = viewModel()
        viewModel.onAddCategory()
        viewModel.onCategoryNameChange("  Postres ")
        viewModel.onSaveCategory()

        assertThat(catalog.categoryWrites).containsExactly(Category("", "Postres", 3))
        assertThat(viewModel.uiState.value.categoryEditor).isNull()
    }

    @Test
    fun aBlankCategoryNameIsRefused() {
        val viewModel = viewModel()
        viewModel.onAddCategory()
        viewModel.onCategoryNameChange("   ")
        viewModel.onSaveCategory()

        assertThat(catalog.categoryWrites).isEmpty()
        assertThat(viewModel.uiState.value.categoryEditor?.nameMissing).isTrue()
    }

    @Test
    fun renamingACategoryKeepsItsIdAndSortOrder() {
        val viewModel = viewModel()
        viewModel.onEditCategory(aCategory("c2", "Bebidas", 2))
        viewModel.onCategoryNameChange("Refrescos")
        viewModel.onSaveCategory()

        assertThat(catalog.categoryWrites).containsExactly(Category("c2", "Refrescos", 2))
    }

    @Test
    fun aCategoryWithProductsCannotBeRemoved() {
        val viewModel = viewModel()

        viewModel.onRemoveCategory("c1")

        assertThat(catalog.removedCategories).isEmpty()
        assertThat(viewModel.uiState.value.error).isEqualTo(MerchantCatalogError.CATEGORY_NOT_EMPTY)
    }

    @Test
    fun anEmptyCategoryIsRemoved() {
        catalog.products.value = emptyList()
        val viewModel = viewModel()

        viewModel.onRemoveCategory("c1")

        assertThat(catalog.removedCategories).containsExactly("c1")
        assertThat(viewModel.uiState.value.error).isNull()
    }
}
