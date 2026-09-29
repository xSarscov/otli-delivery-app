package com.otli.app.auth.domain

/** What the navigation root needs to know to pick a graph or a gate screen. */
sealed interface SessionState {
    data object SignedOut : SessionState
    data object Loading : SessionState
    data object ProfileIncomplete : SessionState
    data class Pending(val role: Role) : SessionState
    data class Suspended(val role: Role) : SessionState
    data class Active(val role: Role) : SessionState
}
