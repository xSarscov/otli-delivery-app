package com.otli.app.catalog.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.adapters.ui.RecordingAuthRepository
import com.otli.app.auth.domain.AuthUser
import com.otli.app.catalog.domain.Category
import com.otli.app.core.money.Money
import com.otli.app.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test

class MerchantCatalogViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val catalog = FakeCatalogRepository()
    private val compressor = FakePhotoCompressor()
    private val auth = RecordingAuthRepository().apply { authState.value = AuthUser("m1", "marta@otli.test") }

    private fun viewModel() = MerchantCatalogViewModel(catalog, compressor, auth)

    private fun MerchantCatalogViewModel.editorOrFail() = checkNotNull(uiState.value.productEditor)

    @Test
    fun showsTheSignedInMerchantsCategoriesAndProducts() {
        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.categories.map { it.name }).containsExactly("Platos", "Bebidas").inOrder()
        assertThat(state.products.map { it.name }).containsExactly("Nacatamal", "Fresco")
    }

    @Test
    fun aRejectedCategoriesListenerSurfacesAsALoadErrorInsteadOfCrashing() {
        catalog.categoriesFeed = flow { throw RuntimeException("PERMISSION_DENIED") }

        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isEqualTo(MerchantCatalogError.LOAD_FAILED)
    }

    @Test
    fun aListenerThatFailsAfterEmittingStillEndsInTheLoadErrorState() {
        catalog.productsFeed = flow {
            emit(listOf(aProduct("p9", "c1", "Vigoron")))
            throw RuntimeException("PERMISSION_DENIED")
        }

        val state = viewModel().uiState.value

        assertThat(state.error).isEqualTo(MerchantCatalogError.LOAD_FAILED)
        assertThat(state.isLoading).isFalse()
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
    fun addingAProductOpensAnEditorForTheChosenCategory() {
        val viewModel = viewModel()

        viewModel.onAddProduct("c2")

        assertThat(viewModel.editorOrFail().categoryId).isEqualTo("c2")
        assertThat(viewModel.editorOrFail().isNew).isTrue()
    }

    @Test
    fun addingAProductWithoutAnyCategoryIsRefused() {
        catalog.categories.value = emptyList()
        val viewModel = viewModel()

        viewModel.onAddProduct(null)

        assertThat(viewModel.uiState.value.productEditor).isNull()
        assertThat(viewModel.uiState.value.error).isEqualTo(MerchantCatalogError.NEEDS_CATEGORY)
    }

    @Test
    fun savingAValidNewProductWritesItWithTheNioPriceAndClosesTheEditor() {
        val viewModel = viewModel()
        viewModel.onAddProduct("c1")
        viewModel.onProductNameChange("Tostones")
        viewModel.onProductPriceChange("45,50")
        viewModel.onSaveProduct()

        val write = catalog.productWrites.single()
        assertThat(write.merchantId).isEqualTo("m1")
        assertThat(write.product.name).isEqualTo("Tostones")
        assertThat(write.product.price).isEqualTo(Money(4550))
        assertThat(write.product.id).isEmpty()
        assertThat(write.photo).isNull()
        assertThat(viewModel.uiState.value.productEditor).isNull()
    }

    @Test
    fun savingAnInvalidProductKeepsTheEditorOpenWithEveryError() {
        val viewModel = viewModel()
        viewModel.onAddProduct("c1")
        viewModel.onProductPriceChange("0")
        viewModel.onSaveProduct()

        assertThat(catalog.productWrites).isEmpty()
        assertThat(viewModel.editorOrFail().errors).containsExactly(
            ProductEditorError.NAME_REQUIRED,
            ProductEditorError.PRICE_INVALID,
        )
    }

    @Test
    fun editingAnExistingProductKeepsItsIdAndUpdatesTheFields() {
        val viewModel = viewModel()
        viewModel.onEditProduct(aProduct("p1", "c1", "Nacatamal", 12000))
        viewModel.onProductNameChange("Nacatamal grande")
        viewModel.onProductPriceChange("150")
        viewModel.onSaveProduct()

        val saved = catalog.productWrites.single().product
        assertThat(saved.id).isEqualTo("p1")
        assertThat(saved.name).isEqualTo("Nacatamal grande")
        assertThat(saved.price).isEqualTo(Money(15000))
    }

    @Test
    fun aPickedPhotoIsCompressedAndSentWithTheProduct() {
        val viewModel = viewModel()
        viewModel.onAddProduct("c1")
        viewModel.onProductNameChange("Con foto")
        viewModel.onProductPriceChange("10")
        viewModel.onProductPhotoPicked(byteArrayOf(1, 2, 3))
        viewModel.onSaveProduct()

        assertThat(catalog.productWrites.single().photo).isEqualTo(byteArrayOf(3, 2, 1))
    }

    @Test
    fun anUnreadablePhotoShowsAnErrorAndKeepsTheEditor() {
        compressor.outcome = { Result.failure(IllegalArgumentException("not an image")) }
        val viewModel = viewModel()
        viewModel.onAddProduct("c1")

        viewModel.onProductPhotoPicked(byteArrayOf(9))

        assertThat(viewModel.uiState.value.error).isEqualTo(MerchantCatalogError.PHOTO_FAILED)
        assertThat(viewModel.editorOrFail().pendingPhoto).isNull()
    }

    @Test
    fun aFailedProductSaveKeepsTheEditorAndShowsAnError() {
        catalog.writeOutcome = { Result.failure(IllegalStateException("offline")) }
        val viewModel = viewModel()
        viewModel.onAddProduct("c1")
        viewModel.onProductNameChange("Tostones")
        viewModel.onProductPriceChange("10")

        viewModel.onSaveProduct()

        assertThat(viewModel.uiState.value.error).isEqualTo(MerchantCatalogError.SAVE_FAILED)
        assertThat(viewModel.uiState.value.productEditor).isNotNull()
        assertThat(viewModel.uiState.value.isSaving).isFalse()
    }

    @Test
    fun aSecondSaveWhileOneIsInFlightIsIgnored() {
        val gate = CompletableDeferred<Result<Unit>>()
        catalog.writeOutcome = { gate.await() }
        val viewModel = viewModel()
        viewModel.onAddProduct("c1")
        viewModel.onProductNameChange("Tostones")
        viewModel.onProductPriceChange("10")
        viewModel.onSaveProduct()

        viewModel.onSaveProduct()
        assertThat(catalog.productWrites).hasSize(1)

        gate.complete(Result.success(Unit))
        assertThat(viewModel.uiState.value.isSaving).isFalse()
    }

    @Test
    fun dismissingTheEditorDiscardsIt() {
        val viewModel = viewModel()
        viewModel.onAddProduct("c1")

        viewModel.onDismissProductEditor()

        assertThat(viewModel.uiState.value.productEditor).isNull()
    }

    @Test
    fun removingAProductDeletesIt() {
        val viewModel = viewModel()

        viewModel.onRemoveProduct("p1")

        assertThat(catalog.removedProducts).containsExactly("p1")
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
