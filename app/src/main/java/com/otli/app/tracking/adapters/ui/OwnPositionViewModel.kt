package com.otli.app.tracking.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.core.map.MapPin
import com.otli.app.tracking.application.LocationSource
import com.otli.app.tracking.domain.GeoFix
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * The courier's own position for the delivery map, from the same [LocationSource] the tracking service
 * publishes. It listens to the device only while the screen is collecting [position] and the screen
 * says a position is [wanted][setWanted] (a delivery is on screen and the location permission is held),
 * so an idle courier home costs no battery. A source that fails (permission revoked) leaves no dot
 * instead of crashing; wanting it again listens anew.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OwnPositionViewModel @Inject constructor(
    source: LocationSource,
) : ViewModel() {
    private val wanted = MutableStateFlow(false)

    val position: StateFlow<MapPin?> = wanted
        .flatMapLatest { isWanted ->
            if (!isWanted) {
                flowOf(null)
            } else {
                source.fixes().map<GeoFix, MapPin?> { MapPin(it.latitude, it.longitude) }.catch { emit(null) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun setWanted(isWanted: Boolean) {
        wanted.value = isWanted
    }
}
