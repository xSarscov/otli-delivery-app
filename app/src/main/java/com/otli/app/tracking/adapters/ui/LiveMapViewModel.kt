package com.otli.app.tracking.adapters.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import com.otli.app.core.map.MapPin
import com.otli.app.ordering.adapters.ui.OrderTrackingViewModel
import com.otli.app.ordering.application.OrderRepository
import com.otli.app.tracking.application.LocationRepository
import com.otli.app.tracking.domain.LiveMapAccess
import com.otli.app.tracking.domain.LivePosition
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class LiveMapUiState(
    val isLoading: Boolean = true,
    /** The viewer may see the courier of this order right now: the map is on screen. */
    val visible: Boolean = false,
    val dropoff: MapPin? = null,
    /** The courier's latest position, null until one is published. */
    val courier: LivePosition? = null,
    /** The position could not be read (its listener was refused); the dropoff is still shown. */
    val locationUnavailable: Boolean = false,
) {
    companion object {
        val Hidden = LiveMapUiState(isLoading = false)
    }
}

/**
 * The live map of one order for its customer (or Admin): the dropoff pin and the courier's position
 * while the order is `claimed` or `picked_up`. [LiveMapAccess] decides whether the viewer may see it,
 * and an unauthorized viewer never even subscribes to the location; the rules stay the final authority.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveMapViewModel @Inject constructor(
    orders: OrderRepository,
    private val locations: LocationRepository,
    auth: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LiveMapUiState())
    val uiState: StateFlow<LiveMapUiState> = _uiState.asStateFlow()

    private val orderId: String? = savedStateHandle.get<String>(OrderTrackingViewModel.ORDER_ID_ARG)?.takeIf { it.isNotBlank() }

    init {
        if (orderId == null) {
            _uiState.value = LiveMapUiState.Hidden
        } else {
            viewModelScope.launch {
                auth.observeAuthState()
                    .map { it?.uid }
                    .distinctUntilChanged()
                    .flatMapLatest { uid ->
                        if (uid == null) {
                            flowOf(LiveMapUiState.Hidden)
                        } else {
                            // Only the dropoff of an order the viewer may follow keeps the location listener alive.
                            combine(orders.observe(orderId), auth.observeUserDocument(uid)) { order, viewer ->
                                order?.takeIf { LiveMapAccess.canView(it, viewer) }?.let { MapPin(it.dropoff.latitude, it.dropoff.longitude) }
                            }
                                .distinctUntilChanged()
                                .flatMapLatest { dropoff -> if (dropoff == null) flowOf(LiveMapUiState.Hidden) else following(orderId, dropoff) }
                        }
                    }
                    // A rejected listener (e.g. the rules deny reads after sign-out) must never crash the app.
                    .catch { emit(LiveMapUiState.Hidden) }
                    .collect { _uiState.value = it }
            }
        }
    }

    private fun following(orderId: String, dropoff: MapPin) = locations.observe(orderId)
        .map { LiveMapUiState(isLoading = false, visible = true, dropoff = dropoff, courier = it) }
        .catch { emit(LiveMapUiState(isLoading = false, visible = true, dropoff = dropoff, locationUnavailable = true)) }
}
