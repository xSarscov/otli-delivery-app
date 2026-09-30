package com.otli.app.auth.application

import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.SessionState
import com.otli.app.auth.domain.UserAccount
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Combines the auth state with the `users/{uid}` document into the [SessionState]
 * that drives the navigation root (see the Session gate diagram in design.md).
 */
class ObserveSessionUseCase @Inject constructor(private val repository: AuthRepository) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<SessionState> =
        repository.observeAuthState()
            .flatMapLatest { user ->
                if (user == null) {
                    flowOf(SessionState.SignedOut)
                } else {
                    repository.observeUserDocument(user.uid)
                        .map(::toSessionState)
                        // The rules reject this read once the user signs out, before the auth state
                        // flips. Fall back to the recoverable gate (it offers retry and sign out); the
                        // auth change then replaces this inner flow with SignedOut.
                        .catch { emit(SessionState.ProfileIncomplete) }
                        .onStart { emit(SessionState.Loading) }
                }
            }
            .catch { emit(SessionState.SignedOut) }
            .onStart { emit(SessionState.Loading) }
            .distinctUntilChanged()

    private fun toSessionState(account: UserAccount?): SessionState = when {
        account == null -> SessionState.ProfileIncomplete
        account.status == AccountStatus.PENDING -> SessionState.Pending(account.role)
        account.status == AccountStatus.SUSPENDED -> SessionState.Suspended(account.role)
        else -> SessionState.Active(account.role)
    }
}
