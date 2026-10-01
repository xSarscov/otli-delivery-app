package com.otli.app.ordering.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import com.otli.app.ordering.application.OrderRepository
import com.otli.app.ordering.domain.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomerOrdersUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** Orders still moving towards the customer, newest first. */
    val active: List<Order> = emptyList(),
    /** Delivered, rejected and cancelled orders, newest first. */
    val past: List<Order> = emptyList(),
)

/** The signed-in customer's own orders. The customer id always comes from the session. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomerOrdersViewModel @Inject constructor(
    orders: OrderRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CustomerOrdersUiState())
    val uiState: StateFlow<CustomerOrdersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            auth.observeAuthState()
                .filterNotNull()
                .map { it.uid }
                .distinctUntilChanged()
                .flatMapLatest { orders.observeForCustomer(it) }
                // A rejected listener (e.g. the rules deny reads after sign-out) must never crash the app.
                .catch { _uiState.update { it.copy(isLoading = false, loadFailed = true) } }
                .collect { list ->
                    val (past, active) = list.partition { it.status.isTerminal }
                    _uiState.value = CustomerOrdersUiState(isLoading = false, active = active, past = past)
                }
        }
    }
}
