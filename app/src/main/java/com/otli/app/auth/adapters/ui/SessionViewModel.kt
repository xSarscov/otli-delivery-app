package com.otli.app.auth.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.ObserveSessionUseCase
import com.otli.app.auth.domain.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Holds the app-wide [SessionState] that drives the navigation root. */
@HiltViewModel
class SessionViewModel @Inject constructor(observeSession: ObserveSessionUseCase) : ViewModel() {
    val session: StateFlow<SessionState> = observeSession().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SessionState.Loading,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
