package com.otli.app.core.navigation

import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.SessionState
import kotlinx.serialization.Serializable

/** Type-safe destinations of the root navigation graph (ADR-5). */
@Serializable data object LoadingRoute

@Serializable data object SignedOutGraph

@Serializable data object SignedOutStart

/** Session gates: signed in, but not allowed into a role graph yet. */
object Gate {
    @Serializable data object ProfileIncomplete

    @Serializable data class PendingApproval(val role: Role)

    @Serializable data class Suspended(val role: Role)
}

@Serializable data object CustomerGraph

@Serializable data object CustomerHome

@Serializable data object MerchantGraph

@Serializable data object MerchantHome

@Serializable data object CourierGraph

@Serializable data object CourierHome

@Serializable data object AdminGraph

@Serializable data object AdminHome

/** Maps a [SessionState] to the single destination it may occupy. Pure, so it is easy to reason about. */
fun routeFor(state: SessionState): Any = when (state) {
    SessionState.Loading -> LoadingRoute
    SessionState.SignedOut -> SignedOutGraph
    SessionState.ProfileIncomplete -> Gate.ProfileIncomplete
    is SessionState.Pending -> Gate.PendingApproval(state.role)
    is SessionState.Suspended -> Gate.Suspended(state.role)
    is SessionState.Active -> when (state.role) {
        Role.CUSTOMER -> CustomerGraph
        Role.MERCHANT -> MerchantGraph
        Role.COURIER -> CourierGraph
        Role.ADMIN -> AdminGraph
    }
}
