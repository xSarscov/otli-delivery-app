package com.otli.app.ordering.adapters.notification

import com.otli.app.auth.application.AuthRepository
import com.otli.app.auth.application.ObserveSessionUseCase
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.SessionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn

/**
 * Keeps the right [LocalOrderNotifier] watch running for the signed-in, approved customer or
 * merchant while the app process lives (ADR-12). Signing out ends the watch; the next user starts
 * their own.
 */
@Singleton
class OrderNotificationCoordinator @Inject constructor(
    private val session: ObserveSessionUseCase,
    private val auth: AuthRepository,
    private val notifier: LocalOrderNotifier,
) {
    private data class Target(val role: Role, val uid: String)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope): Job =
        combine(session(), auth.observeAuthState()) { state, user ->
            val role = (state as? SessionState.Active)?.role
            if (role != null && user != null) Target(role, user.uid) else null
        }
            .distinctUntilChanged()
            .flatMapLatest { target -> watch(target) }
            .catch { }
            .launchIn(scope)

    private fun watch(target: Target?) = when (target?.role) {
        Role.CUSTOMER -> flow<Unit> { notifier.watchCustomer(target.uid) }
        Role.MERCHANT -> flow<Unit> { notifier.watchMerchant(target.uid) }
        else -> emptyFlow()
    }
}
