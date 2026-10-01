package com.otli.app.ordering.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import com.otli.app.ordering.application.OrderRepository
import com.otli.app.ordering.domain.Actor
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.ordering.domain.OrderTransitions
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

enum class OrderBoardError { LOAD_FAILED, ACTION_FAILED }

data class MerchantOrderBoardUiState(
    val isLoading: Boolean = true,
    /** Orders waiting for the merchant's answer, oldest first. */
    val incoming: List<Order> = emptyList(),
    /** Accepted orders up to the ones already with a courier, oldest first. */
    val inProgress: List<Order> = emptyList(),
    /** The order whose rejection dialog is open. */
    val rejecting: String? = null,
    val rejectReason: String = "",
    val rejectReasonMissing: Boolean = false,
    /** Orders with a step in flight; their buttons are disabled. */
    val busy: Set<String> = emptySet(),
    val error: OrderBoardError? = null,
)

/**
 * The signed-in merchant's live order board. The merchant id always comes from the session; the
 * rules stay the final authority on every transition.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MerchantOrderBoardViewModel @Inject constructor(
    private val orders: OrderRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MerchantOrderBoardUiState())
    val uiState: StateFlow<MerchantOrderBoardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            auth.observeAuthState()
                .filterNotNull()
                .map { it.uid }
                .distinctUntilChanged()
                .flatMapLatest { orders.observeForMerchant(it) }
                // A rejected listener (e.g. the rules deny reads after sign-out) must never crash the app.
                .catch { _uiState.update { it.copy(isLoading = false, error = OrderBoardError.LOAD_FAILED) } }
                .collect { list -> _uiState.update { show(it, list) } }
        }
    }

    /** Takes the next step: accept a placed order, then start preparing it, then mark it ready. */
    fun advance(orderId: String) {
        val order = find(orderId) ?: return
        val next = OrderTransitions.merchantAdvance(order.status) ?: return
        send(orderId, next, reason = null)
    }

    fun startReject(orderId: String) {
        if (find(orderId)?.status != OrderStatus.PLACED) return
        _uiState.update { it.copy(rejecting = orderId, rejectReason = "", rejectReasonMissing = false) }
    }

    fun setRejectReason(reason: String) = _uiState.update { it.copy(rejectReason = reason, rejectReasonMissing = false) }

    fun dismissReject() = _uiState.update { it.copy(rejecting = null, rejectReason = "", rejectReasonMissing = false) }

    fun confirmReject() {
        val state = _uiState.value
        val orderId = state.rejecting ?: return
        val reason = state.rejectReason.trim()
        if (reason.isEmpty()) {
            _uiState.update { it.copy(rejectReasonMissing = true) }
            return
        }
        send(orderId, OrderStatus.REJECTED, reason) { dismissReject() }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    private fun send(orderId: String, to: OrderStatus, reason: String?, onSent: () -> Unit = {}) {
        if (orderId in _uiState.value.busy) return
        _uiState.update { it.copy(busy = it.busy + orderId, error = null) }
        viewModelScope.launch {
            val result = orders.transition(orderId, to, Actor.MERCHANT, reason)
            _uiState.update {
                it.copy(busy = it.busy - orderId, error = if (result.isFailure) OrderBoardError.ACTION_FAILED else it.error)
            }
            if (result.isSuccess) onSent()
        }
    }

    private fun find(orderId: String): Order? =
        with(_uiState.value) { (incoming + inProgress).firstOrNull { it.id == orderId } }

    private fun show(state: MerchantOrderBoardUiState, list: List<Order>): MerchantOrderBoardUiState {
        val oldestFirst = list.sortedBy { it.createdAtMillis }
        val incoming = oldestFirst.filter { it.status == OrderStatus.PLACED }
        val stillWaiting = state.rejecting != null && incoming.any { it.id == state.rejecting }
        return state.copy(
            isLoading = false,
            incoming = incoming,
            inProgress = oldestFirst.filter { it.status in IN_PROGRESS },
            rejecting = state.rejecting.takeIf { stillWaiting },
            rejectReason = if (stillWaiting) state.rejectReason else "",
            rejectReasonMissing = stillWaiting && state.rejectReasonMissing,
        )
    }

    private companion object {
        val IN_PROGRESS = setOf(
            OrderStatus.ACCEPTED,
            OrderStatus.PREPARING,
            OrderStatus.READY,
            OrderStatus.CLAIMED,
            OrderStatus.PICKED_UP,
        )
    }
}
