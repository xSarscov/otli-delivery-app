package com.otli.app.dispatch.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import com.otli.app.dispatch.application.DispatchRepository
import com.otli.app.dispatch.domain.ClaimDecision
import com.otli.app.dispatch.domain.ClaimDenial
import com.otli.app.dispatch.domain.PoolGate
import com.otli.app.dispatch.domain.PoolOrder
import com.otli.app.ordering.domain.OrderOrdering
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

/** What the courier is told after an attempt that did not claim the order, or when the pool cannot load. */
enum class PoolMessage { ALREADY_TAKEN, NOT_READY, BUSY, OFFLINE, NOT_ACTIVE, CLAIM_FAILED, LOAD_FAILED }

data class PoolUiState(
    val isLoading: Boolean = true,
    val gate: PoolGate = PoolGate.OFFLINE,
    /** The claimable orders, newest placed first; empty unless the [gate] is open. */
    val orders: List<PoolOrder> = emptyList(),
    /** Orders with a claim in flight; their buttons are disabled. */
    val claiming: Set<String> = emptySet(),
    val message: PoolMessage? = null,
)

/**
 * The open pool of the signed-in courier. The pool listener only runs while the courier is online
 * and free, so an offline or busy courier never reads (or sees) it. The courier id always comes from
 * the session; the claim transaction and the rules stay the authority on who wins.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PoolViewModel @Inject constructor(
    private val dispatch: DispatchRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PoolUiState())
    val uiState: StateFlow<PoolUiState> = _uiState.asStateFlow()

    private var courierId: String? = null

    init {
        viewModelScope.launch {
            auth.observeAuthState()
                .map { it?.uid }
                .distinctUntilChanged()
                .onEach { courierId = it }
                .filterNotNull()
                .flatMapLatest { uid -> dispatch.observeCourier(uid).map { PoolGate.of(it) }.distinctUntilChanged() }
                .flatMapLatest { gate ->
                    if (gate == PoolGate.OPEN) dispatch.observePool().map { gate to it } else flowOf(gate to emptyList())
                }
                // A rejected listener (e.g. the rules deny reads after sign-out) must never crash the app.
                .catch { _uiState.update { it.copy(isLoading = false, message = PoolMessage.LOAD_FAILED) } }
                .collect { (gate, orders) ->
                    _uiState.update { it.copy(isLoading = false, gate = gate, orders = newestFirst(orders)) }
                }
        }
    }

    fun claim(orderId: String) {
        val id = courierId ?: return
        val state = _uiState.value
        // The list is empty unless the pool is open, so an offline or busy courier cannot claim anything.
        if (orderId in state.claiming || state.orders.none { it.id == orderId }) return
        _uiState.update { it.copy(claiming = it.claiming + orderId, message = null) }
        viewModelScope.launch {
            val result = dispatch.claim(orderId, id)
            _uiState.update { it.copy(claiming = it.claiming - orderId, message = messageFor(result)) }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    private fun messageFor(result: Result<ClaimDecision>): PoolMessage? {
        val decision = result.getOrElse { return PoolMessage.CLAIM_FAILED }
        return when (decision) {
            ClaimDecision.Allowed -> null
            is ClaimDecision.Denied -> when (decision.reason) {
                ClaimDenial.NOT_READY -> PoolMessage.NOT_READY
                ClaimDenial.ALREADY_CLAIMED -> PoolMessage.ALREADY_TAKEN
                ClaimDenial.COURIER_BUSY -> PoolMessage.BUSY
                ClaimDenial.COURIER_OFFLINE -> PoolMessage.OFFLINE
                ClaimDenial.COURIER_NOT_ACTIVE -> PoolMessage.NOT_ACTIVE
            }
        }
    }

    /** The same newest-placed-first ordering as every other order list (see [OrderOrdering]). */
    private fun newestFirst(orders: List<PoolOrder>): List<PoolOrder> =
        OrderOrdering.newestFirst(orders, PoolOrder::createdAtMillis, PoolOrder::id)
}
