package com.otli.app.dispatch.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import com.otli.app.dispatch.application.DispatchRepository
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AvailabilityError { LOAD_FAILED, UPDATE_FAILED }

data class AvailabilityUiState(
    val isLoading: Boolean = true,
    val isOnline: Boolean = false,
    /** A claimed or picked-up order is in progress; the courier cannot go offline until it is delivered. */
    val hasActiveOrder: Boolean = false,
    /** A change is in flight; the switch is locked. */
    val isUpdating: Boolean = false,
    val error: AvailabilityError? = null,
)

/**
 * The signed-in courier's online/offline switch. The courier id always comes from the session; the
 * rules stay the final authority (going offline is refused while a delivery is under way).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AvailabilityViewModel @Inject constructor(
    private val dispatch: DispatchRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AvailabilityUiState())
    val uiState: StateFlow<AvailabilityUiState> = _uiState.asStateFlow()

    private var courierId: String? = null

    init {
        viewModelScope.launch {
            auth.observeAuthState()
                .map { it?.uid }
                .distinctUntilChanged()
                .onEach { courierId = it }
                .filterNotNull()
                .flatMapLatest { dispatch.observeCourier(it) }
                // A rejected listener (e.g. the rules deny reads after sign-out) must never crash the app.
                .catch { _uiState.update { it.copy(isLoading = false, error = AvailabilityError.LOAD_FAILED) } }
                .collect { courier ->
                    _uiState.update {
                        it.copy(isLoading = false, isOnline = courier?.isOnline == true, hasActiveOrder = courier?.isBusy == true)
                    }
                }
        }
    }

    fun setOnline(online: Boolean) {
        val id = courierId ?: return
        val state = _uiState.value
        if (state.isLoading || state.isUpdating) return
        if (!online && state.hasActiveOrder) return
        _uiState.update { it.copy(isUpdating = true, error = null) }
        viewModelScope.launch {
            val result = dispatch.setOnline(id, online)
            _uiState.update {
                it.copy(isUpdating = false, error = if (result.isFailure) AvailabilityError.UPDATE_FAILED else it.error)
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
