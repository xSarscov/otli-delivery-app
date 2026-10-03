package com.otli.app.tracking.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.tracking.application.TrackingCoordinator
import com.otli.app.tracking.domain.TrackingPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs [TrackingCoordinator] while the courier home is on screen (Android only lets the app start the
 * location service while it is visible, ADR-8) and exposes the plan so the screen can say whether the
 * location is being shared. The location permission is owned by the UI and reported here.
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val coordinator: TrackingCoordinator,
) : ViewModel() {
    private val permitted = MutableStateFlow(false)
    // The device location switch is wired in the next unit; until then it is assumed on.
    private val servicesOn = MutableStateFlow(true)
    private val _plan = MutableStateFlow<TrackingPlan>(TrackingPlan.Idle)
    val plan: StateFlow<TrackingPlan> = _plan.asStateFlow()

    init {
        viewModelScope.launch {
            coordinator.plans(permitted, servicesOn).collect {
                _plan.value = it
                coordinator.apply(it)
            }
        }
    }

    fun onPermissionChanged(granted: Boolean) {
        permitted.value = granted
    }
}
