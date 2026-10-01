package com.otli.app.ordering.adapters.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.ordering.application.OrderRepository
import com.otli.app.ordering.domain.Actor
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TrackingError { CANCEL_FAILED }

data class OrderTrackingUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val loadFailed: Boolean = false,
    val order: Order? = null,
    val isCancelling: Boolean = false,
    val error: TrackingError? = null,
) {
    /** A customer may only cancel while the store has not answered yet (ordering spec). */
    val cancelEnabled: Boolean get() = order?.status == OrderStatus.PLACED && !isCancelling
}

/** One order for its customer: its live status and the cancel action. The rules decide who may read it. */
@HiltViewModel
class OrderTrackingViewModel @Inject constructor(
    private val orders: OrderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OrderTrackingUiState())
    val uiState: StateFlow<OrderTrackingUiState> = _uiState.asStateFlow()

    private val orderId: String? = savedStateHandle.get<String>(ORDER_ID_ARG)?.takeIf { it.isNotBlank() }

    init {
        if (orderId == null) {
            _uiState.value = OrderTrackingUiState(isLoading = false, notFound = true)
        } else {
            viewModelScope.launch {
                orders.observe(orderId)
                    // A rejected listener (e.g. the rules deny reads after sign-out) must never crash the app.
                    .catch { _uiState.update { it.copy(isLoading = false, loadFailed = true) } }
                    .collect { order ->
                        _uiState.update { it.copy(isLoading = false, notFound = order == null, loadFailed = false, order = order) }
                    }
            }
        }
    }

    fun cancel() {
        val id = orderId ?: return
        if (!_uiState.value.cancelEnabled) return
        _uiState.update { it.copy(isCancelling = true, error = null) }
        viewModelScope.launch {
            val result = orders.transition(id, OrderStatus.CANCELLED, Actor.CUSTOMER)
            _uiState.update {
                it.copy(isCancelling = false, error = if (result.isFailure) TrackingError.CANCEL_FAILED else it.error)
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    companion object {
        /** The navigation argument holding the order id; [OrderTrackingRoute] uses the same key. */
        const val ORDER_ID_ARG = "orderId"
    }
}
