package com.otli.app.catalog.adapters.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.catalog.application.CatalogRepository
import com.otli.app.catalog.application.Storefront
import com.otli.app.catalog.domain.Category
import com.otli.app.catalog.domain.Merchant
import com.otli.app.catalog.domain.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A product row; [canAddToCart] is the single rule the UI and the future cart both rely on. */
data class StorefrontItem(val product: Product, val canAddToCart: Boolean)

data class StorefrontSection(val category: Category, val items: List<StorefrontItem>)

data class StorefrontUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val loadFailed: Boolean = false,
    val merchant: Merchant? = null,
    val sections: List<StorefrontSection> = emptyList(),
) {
    val isClosed: Boolean get() = merchant?.isOpen == false
}

/**
 * One merchant's storefront for a customer. Products stay listed when unavailable so the customer
 * sees them, but only an available product of an open store can be added to a cart
 * (merchant-catalog spec: closed stores and unavailable products are never addable).
 */
@HiltViewModel
class StorefrontViewModel @Inject constructor(
    catalog: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StorefrontUiState())
    val uiState: StateFlow<StorefrontUiState> = _uiState.asStateFlow()

    init {
        val merchantId = savedStateHandle.get<String>(MERCHANT_ID_ARG)
        if (merchantId.isNullOrBlank()) {
            _uiState.value = StorefrontUiState(isLoading = false, notFound = true)
        } else {
            viewModelScope.launch {
                catalog.observeStorefront(merchantId)
                    .catch { _uiState.update { it.copy(isLoading = false, loadFailed = true) } }
                    .collect { _uiState.value = it?.toUiState() ?: StorefrontUiState(isLoading = false, notFound = true) }
            }
        }
    }

    private fun Storefront.toUiState(): StorefrontUiState {
        val sections = categories.mapNotNull { category ->
            val items = products.filter { it.categoryId == category.id }
                .map { StorefrontItem(it, canAddToCart = merchant.isOpen && it.isAvailable) }
            items.takeIf { it.isNotEmpty() }?.let { StorefrontSection(category, it) }
        }
        return StorefrontUiState(isLoading = false, merchant = merchant, sections = sections)
    }

    companion object {
        /** Navigation argument name of the storefront route. */
        const val MERCHANT_ID_ARG = "merchantId"
    }
}
