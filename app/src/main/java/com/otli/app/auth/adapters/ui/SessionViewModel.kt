package com.otli.app.auth.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.ObserveSessionUseCase
import com.otli.app.auth.domain.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Holds the app-wide [SessionState] that drives the navigation root. */
@HiltViewModel
class SessionViewModel @Inject constructor(observeSession: ObserveSessionUseCase) : ViewModel() {
    private val reads = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val session: StateFlow<SessionState> = reads
        .flatMapLatest { observeSession() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SessionState.Loading,
        )

    /** Resolves the session again from scratch, e.g. when the profile document could not be found. */
    fun retry() {
        reads.update { it + 1 }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
