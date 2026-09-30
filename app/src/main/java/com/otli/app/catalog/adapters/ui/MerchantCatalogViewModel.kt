package com.otli.app.catalog.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import com.otli.app.catalog.application.CatalogRepository
import com.otli.app.catalog.application.PhotoCompressor
import com.otli.app.catalog.domain.CatalogValidation
import com.otli.app.catalog.domain.Category
import com.otli.app.catalog.domain.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MerchantCatalogError {
    NEEDS_CATEGORY,
    CATEGORY_NOT_EMPTY,
    SAVE_FAILED,
    DELETE_FAILED,
    AVAILABILITY_FAILED,
    PHOTO_FAILED,
    LOAD_FAILED,
}

/** The category form being edited; [id] is blank for a new category. */
data class CategoryEditorState(
    val id: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
    val nameMissing: Boolean = false,
)

data class MerchantCatalogUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val productEditor: ProductEditorState? = null,
    val categoryEditor: CategoryEditorState? = null,
    val isSaving: Boolean = false,
    val error: MerchantCatalogError? = null,
)

/**
 * Manages the signed-in merchant's own catalog. The merchant id always comes from the session,
 * so no screen path can address another merchant's items (merchant-catalog spec).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MerchantCatalogViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val photos: PhotoCompressor,
    auth: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MerchantCatalogUiState())
    val uiState: StateFlow<MerchantCatalogUiState> = _uiState.asStateFlow()

    private var merchantId: String? = null

    init {
        viewModelScope.launch {
            auth.observeAuthState()
                .filterNotNull()
                .map { it.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    merchantId = uid
                    combine(catalog.observeCategories(uid), catalog.observeProducts(uid)) { categories, products ->
                        categories to products
                    }
                }
                // A rejected listener (e.g. rules deny reads after sign-out) must never crash the app.
                .catch { _uiState.update { it.copy(isLoading = false, error = MerchantCatalogError.LOAD_FAILED) } }
                .collect { (categories, products) ->
                    _uiState.update { it.copy(isLoading = false, categories = categories, products = products) }
                }
        }
    }

    fun onDismissError() = _uiState.update { it.copy(error = null) }

    fun onAvailabilityChange(productId: String, available: Boolean) {
        val id = merchantId ?: return
        viewModelScope.launch {
            if (catalog.setProductAvailability(id, productId, available).isFailure) fail(MerchantCatalogError.AVAILABILITY_FAILED)
        }
    }

    // --- products ---

    fun onAddProduct(categoryId: String?) {
        val chosen = categoryId ?: _uiState.value.categories.firstOrNull()?.id
        if (chosen == null) {
            fail(MerchantCatalogError.NEEDS_CATEGORY)
            return
        }
        _uiState.update { it.copy(productEditor = ProductEditorState.forNew(chosen), error = null) }
    }

    fun onEditProduct(product: Product) =
        _uiState.update { it.copy(productEditor = ProductEditorState.from(product), error = null) }

    fun onProductNameChange(value: String) = editProduct { copy(name = value) }

    fun onProductDescriptionChange(value: String) = editProduct { copy(description = value) }

    fun onProductPriceChange(value: String) = editProduct { copy(priceText = value) }

    fun onProductCategoryChange(categoryId: String) = editProduct { copy(categoryId = categoryId) }

    /** [picked] is the raw image; it is compressed now so an unreadable file fails before saving. */
    fun onProductPhotoPicked(picked: ByteArray) {
        photos.compress(picked).fold(
            onSuccess = { jpeg -> editProduct { copy(pendingPhoto = jpeg) } },
            onFailure = { fail(MerchantCatalogError.PHOTO_FAILED) },
        )
    }

    fun onDismissProductEditor() = _uiState.update { it.copy(productEditor = null) }

    fun onSaveProduct() {
        val id = merchantId ?: return
        val state = _uiState.value
        val editor = state.productEditor ?: return
        if (state.isSaving) return
        val product = when (val result = editor.validate()) {
            is ProductEditorResult.Invalid -> {
                _uiState.update { it.copy(productEditor = editor.copy(errors = result.errors)) }
                return
            }
            is ProductEditorResult.Valid -> result.product
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = catalog.upsertProduct(id, product, editor.pendingPhoto)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    productEditor = if (result.isSuccess) null else it.productEditor,
                    error = if (result.isFailure) MerchantCatalogError.SAVE_FAILED else null,
                )
            }
        }
    }

    fun onRemoveProduct(productId: String) {
        val id = merchantId ?: return
        viewModelScope.launch {
            if (catalog.removeProduct(id, productId).isFailure) fail(MerchantCatalogError.DELETE_FAILED)
        }
    }

    // --- categories ---

    fun onAddCategory() {
        val next = (_uiState.value.categories.maxOfOrNull { it.sortOrder } ?: 0) + 1
        _uiState.update { it.copy(categoryEditor = CategoryEditorState(sortOrder = next), error = null) }
    }

    fun onEditCategory(category: Category) = _uiState.update {
        it.copy(categoryEditor = CategoryEditorState(category.id, category.name, category.sortOrder), error = null)
    }

    fun onCategoryNameChange(value: String) = _uiState.update {
        it.copy(categoryEditor = it.categoryEditor?.copy(name = value, nameMissing = false))
    }

    fun onDismissCategoryEditor() = _uiState.update { it.copy(categoryEditor = null) }

    fun onSaveCategory() {
        val id = merchantId ?: return
        val state = _uiState.value
        val editor = state.categoryEditor ?: return
        if (state.isSaving) return
        val name = editor.name.trim()
        if (CatalogValidation.validateName(name) != null) {
            _uiState.update { it.copy(categoryEditor = editor.copy(nameMissing = true)) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = catalog.upsertCategory(id, Category(editor.id, name, editor.sortOrder))
            _uiState.update {
                it.copy(
                    isSaving = false,
                    categoryEditor = if (result.isSuccess) null else it.categoryEditor,
                    error = if (result.isFailure) MerchantCatalogError.SAVE_FAILED else null,
                )
            }
        }
    }

    /** A category that still holds products is refused: the rules would not stop it, and it would orphan them. */
    fun onRemoveCategory(categoryId: String) {
        val id = merchantId ?: return
        if (_uiState.value.products.any { it.categoryId == categoryId }) {
            fail(MerchantCatalogError.CATEGORY_NOT_EMPTY)
            return
        }
        viewModelScope.launch {
            if (catalog.removeCategory(id, categoryId).isFailure) fail(MerchantCatalogError.DELETE_FAILED)
        }
    }

    private fun fail(error: MerchantCatalogError) = _uiState.update { it.copy(error = error) }

    private fun editProduct(change: ProductEditorState.() -> ProductEditorState) = _uiState.update { state ->
        state.copy(productEditor = state.productEditor?.change()?.copy(errors = emptySet()), error = null)
    }
}
