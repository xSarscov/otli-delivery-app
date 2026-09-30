package com.otli.app.catalog.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.catalog.application.MerchantRepository
import com.otli.app.catalog.domain.Merchant
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MerchantListUiState(
    val isLoading: Boolean = true,
    val merchants: List<Merchant> = emptyList(),
    val loadFailed: Boolean = false,
)

/** The customer's browsing entry point: every active merchant, open or closed, kept live. */
@HiltViewModel
class MerchantListViewModel @Inject constructor(merchants: MerchantRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(MerchantListUiState())
    val uiState: StateFlow<MerchantListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            merchants.observeMerchantsList()
                .catch { _uiState.update { it.copy(isLoading = false, loadFailed = true) } }
                .collect { list -> _uiState.value = MerchantListUiState(isLoading = false, merchants = list) }
        }
    }
}
