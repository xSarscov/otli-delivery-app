package com.otli.app.dispatch.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import com.otli.app.dispatch.application.DispatchRepository
import com.otli.app.ordering.application.OrderRepository
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeliveryError { LOAD_FAILED, ACTION_FAILED }

data class ActiveDeliveryUiState(
    val isLoading: Boolean = true,
    /** The order in the courier's slot, or null when the courier has no active order. */
    val order: Order? = null,
    /** A step is in flight; both buttons are locked. */
    val isBusy: Boolean = false,
    val error: DeliveryError? = null,
) {
    /** Picking up is only possible from `claimed`. */
    val canPickUp: Boolean get() = !isBusy && order?.status == OrderStatus.CLAIMED

    /** Delivering is only possible from `picked_up`, never straight from `claimed`. */
    val canDeliver: Boolean get() = !isBusy && order?.status == OrderStatus.PICKED_UP
}

/**
 * The delivery the signed-in courier is working on: the order named by their `activeOrderId`, live,
 * with the two steps (picked up, delivered). Delivering frees the slot, which removes this screen and
 * brings the pool back. The rules stay the final authority on every step.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActiveDeliveryViewModel @Inject constructor(
    private val dispatch: DispatchRepository,
    orders: OrderRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ActiveDeliveryUiState())
    val uiState: StateFlow<ActiveDeliveryUiState> = _uiState.asStateFlow()

    private var courierId: String? = null

    init {
        viewModelScope.launch {
            auth.observeAuthState()
                .map { it?.uid }
                .distinctUntilChanged()
                .onEach { courierId = it }
                .filterNotNull()
                .flatMapLatest { uid -> dispatch.observeCourier(uid).map { it?.activeOrderId }.distinctUntilChanged() }
                .flatMapLatest { activeId ->
                    if (activeId == null) flowOf(activeId to null) else orders.observe(activeId).map { activeId to it }
                }
                // A rejected listener (e.g. the rules deny reads after sign-out) must never crash the app.
                .catch { _uiState.update { it.copy(isLoading = false, error = DeliveryError.LOAD_FAILED) } }
                .collect { (activeId, order) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            order = order,
                            error = when {
                                // The slot names an order the courier cannot read.
                                activeId != null && order == null -> DeliveryError.LOAD_FAILED
                                it.error == DeliveryError.LOAD_FAILED -> null
                                else -> it.error
                            },
                        )
                    }
                }
        }
    }

    fun pickUp() = step(_uiState.value.canPickUp) { orderId, courier -> dispatch.markPickedUp(orderId, courier) }

    fun deliver() = step(_uiState.value.canDeliver) { orderId, courier -> dispatch.markDelivered(orderId, courier) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    private fun step(allowed: Boolean, action: suspend (orderId: String, courierId: String) -> Result<Unit>) {
        val courier = courierId ?: return
        val orderId = _uiState.value.order?.id ?: return
        if (!allowed) return
        _uiState.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val result = action(orderId, courier)
            _uiState.update {
                it.copy(isBusy = false, error = if (result.isFailure) DeliveryError.ACTION_FAILED else it.error)
            }
        }
    }
}
