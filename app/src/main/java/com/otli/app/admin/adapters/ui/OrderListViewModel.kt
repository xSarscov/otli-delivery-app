package com.otli.app.admin.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.admin.application.AdminRepository
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderOrdering
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderListUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** The latest orders of every merchant and customer, newest first. */
    val orders: List<Order> = emptyList(),
)

/** Admin's support list: every order with its current status, whoever placed it. */
@HiltViewModel
class OrderListViewModel @Inject constructor(admin: AdminRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(OrderListUiState())
    val uiState: StateFlow<OrderListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            admin.observeAllOrders()
                // A rejected listener (e.g. the rules deny reads after sign-out) must never crash the app.
                .catch { _uiState.update { it.copy(isLoading = false, loadFailed = true) } }
                .collect { orders -> _uiState.value = OrderListUiState(isLoading = false, orders = OrderOrdering.newestFirst(orders)) }
        }
    }
}
