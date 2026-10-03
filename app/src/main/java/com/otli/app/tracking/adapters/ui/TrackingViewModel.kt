package com.otli.app.tracking.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.tracking.application.LocationSettingsChecker
import com.otli.app.tracking.application.TrackingCoordinator
import com.otli.app.tracking.domain.LocationAction
import com.otli.app.tracking.domain.LocationPrompts
import com.otli.app.tracking.domain.TrackingPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Runs [TrackingCoordinator] while the courier home is on screen (Android only lets the app start the
 * location service while it is visible, ADR-8) and exposes the plan so the screen can say whether the
 * location is being shared. The location permission is owned by the UI and reported here; the device
 * location switch comes from [LocationSettingsChecker]. [events] are the one-shot requests the UI
 * answers with a system dialog (see [LocationPrompts]); they wait for a collector.
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val coordinator: TrackingCoordinator,
    settings: LocationSettingsChecker,
) : ViewModel() {
    private val permitted = MutableStateFlow(false)
    private val servicesOn = MutableStateFlow(true)
    private val prompts = LocationPrompts()
    private val _plan = MutableStateFlow<TrackingPlan>(TrackingPlan.Idle)
    private val _events = Channel<LocationAction>(Channel.BUFFERED)
    val plan: StateFlow<TrackingPlan> = _plan.asStateFlow()
    val events: Flow<LocationAction> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            // If the switch cannot be read the courier is not nagged: location is assumed on.
            settings.servicesEnabled().catch { emit(true) }.collect { servicesOn.value = it }
        }
        viewModelScope.launch {
            coordinator.plans(permitted, servicesOn).collect {
                _plan.value = it
                coordinator.apply(it)
                prompts.planChanged(it)?.let(::emit)
            }
        }
    }

    fun onPermissionChanged(granted: Boolean) {
        permitted.value = granted
        prompts.permissionChanged(granted, servicesOn.value)?.let(::emit)
    }

    /** The courier asked to go online: ask for what is missing, the permission first. */
    fun onWentOnline() {
        prompts.wentOnline(permitted.value, servicesOn.value)?.let(::emit)
    }

    private fun emit(action: LocationAction) {
        _events.trySend(action)
    }
}
