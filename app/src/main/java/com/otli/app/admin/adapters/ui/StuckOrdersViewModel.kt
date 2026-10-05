package com.otli.app.admin.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.admin.application.AdminActionResult
import com.otli.app.admin.application.AdminRejection
import com.otli.app.admin.application.AdminRepository
import com.otli.app.admin.application.CancelOrder
import com.otli.app.admin.application.ReleaseClaim
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderOrdering
import com.otli.app.ordering.domain.OrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StuckOrdersError { ACTION_FAILED }

data class StuckOrdersUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** `ready` orders no courier has claimed: Admin can cancel them. Newest first. */
    val waiting: List<Order> = emptyList(),
    /** `claimed` orders: Admin can release them back to the pool. Newest first. */
    val withCourier: List<Order> = emptyList(),
    /** `placed`, `accepted` and `preparing` orders the store has not finished: Admin can cancel them. Newest first. */
    val inKitchen: List<Order> = emptyList(),
    /** `picked_up` orders: a courier took the food and has not delivered it. Admin can cancel them if the courier vanished. Newest first. */
    val pickedUp: List<Order> = emptyList(),
    /** The order an action is running for; nothing else can be acted on meanwhile. */
    val busyOrderId: String? = null,
    /** The order whose cancellation reason Admin is entering; the reason dialog is open while this is set. */
    val cancelTarget: Order? = null,
    /** The last reason was blank or too long; the dialog stays open. */
    val reasonInvalid: Boolean = false,
    val error: StuckOrdersError? = null,
)

/**
 * The orders Admin can still act on: those nobody is moving (a `ready` order no courier takes, a store
 * that never answers), the claimed ones to release and the picked-up ones whose courier may have vanished
 * (cancelled, never returned to the pool). Every list is ordered through [OrderOrdering].
 */
@HiltViewModel
class StuckOrdersViewModel @Inject constructor(
    admin: AdminRepository,
    private val releaseClaim: ReleaseClaim,
    private val cancelOrder: CancelOrder,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StuckOrdersUiState())
    val uiState: StateFlow<StuckOrdersUiState> = _uiState.asStateFlow()

    private var latest: Map<String, Order> = emptyMap()

    init {
        viewModelScope.launch {
            admin.observeStuckOrders()
                // A rejected listener (e.g. the rules deny reads after sign-out) must never crash the app.
                .catch { _uiState.update { it.copy(isLoading = false, loadFailed = true) } }
                .collect { orders ->
                    latest = orders.associateBy { it.id }
                    val newestFirst = OrderOrdering.newestFirst(orders)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadFailed = false,
                            waiting = newestFirst.filter { order -> order.status == OrderStatus.READY },
                            withCourier = newestFirst.filter { order -> order.status == OrderStatus.CLAIMED },
                            inKitchen = newestFirst.filter { order -> order.status in KITCHEN },
                            pickedUp = newestFirst.filter { order -> order.status == OrderStatus.PICKED_UP },
                        )
                    }
                }
        }
    }

    fun release(order: Order) = run(order.id, { releaseClaim(order) })

    fun startCancel(order: Order) = _uiState.update { it.copy(cancelTarget = order, reasonInvalid = false, error = null) }

    fun dismissCancel() = _uiState.update { it.copy(cancelTarget = null, reasonInvalid = false) }

    /** Cancels the open dialog's order with [reason], judged against the order's latest status. */
    fun confirmCancel(reason: String) {
        val target = _uiState.value.cancelTarget ?: return
        val current = latest[target.id]
        if (current == null) {
            _uiState.update { it.copy(cancelTarget = null, error = StuckOrdersError.ACTION_FAILED) }
            return
        }
        run(current.id, { cancelOrder(current, reason) }, ::afterCancel)
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    private fun run(
        orderId: String,
        action: suspend () -> AdminActionResult,
        settle: (StuckOrdersUiState, AdminActionResult) -> StuckOrdersUiState = ::failureAsError,
    ) {
        if (_uiState.value.busyOrderId != null) return
        _uiState.update { it.copy(busyOrderId = orderId, error = null) }
        viewModelScope.launch {
            val outcome = action()
            _uiState.update { settle(it.copy(busyOrderId = null), outcome) }
        }
    }

    private fun failureAsError(state: StuckOrdersUiState, outcome: AdminActionResult) =
        if (outcome == AdminActionResult.Done) state else state.copy(error = StuckOrdersError.ACTION_FAILED)

    /** A bad reason is fixed in place, so the dialog stays open; any other outcome ends it. */
    private fun afterCancel(state: StuckOrdersUiState, outcome: AdminActionResult): StuckOrdersUiState =
        if (outcome is AdminActionResult.Rejected && outcome.reason in REASON_PROBLEMS) {
            state.copy(reasonInvalid = true)
        } else {
            failureAsError(state.copy(cancelTarget = null, reasonInvalid = false), outcome)
        }

    private companion object {
        val KITCHEN = setOf(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING)
        val REASON_PROBLEMS = setOf(AdminRejection.REASON_REQUIRED, AdminRejection.REASON_TOO_LONG)
    }
}
