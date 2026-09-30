package com.otli.app.auth.adapters.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.application.AuthRepository
import com.otli.app.auth.application.ObserveSessionUseCase
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.AuthUser
import com.otli.app.auth.domain.MerchantStoreDetails
import com.otli.app.auth.domain.ProfileFields
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.SessionState
import com.otli.app.auth.domain.UserAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test

private class SessionFakeRepository : AuthRepository {
    val authState = MutableStateFlow<AuthUser?>(null)
    val document = MutableStateFlow<UserAccount?>(null)

    override fun observeAuthState(): Flow<AuthUser?> = authState
    override fun observeUserDocument(uid: String): Flow<UserAccount?> = document
    override suspend fun register(
        email: String,
        password: String,
        role: Role,
        profileFields: ProfileFields,
        merchantStore: MerchantStoreDetails?,
    ) = Result.success(Unit)
    override suspend fun login(email: String, password: String) = Result.success(Unit)
    override suspend fun logout() {
        authState.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val repo = SessionFakeRepository()

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun exposesTheResolvedSessionStateStartingFromLoading() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        repo.authState.value = AuthUser("u1", "u1@otli.test")
        repo.document.value = UserAccount("u1", Role.CUSTOMER, AccountStatus.ACTIVE, "Ana", "u1@otli.test", "8888-0000")
        val viewModel = SessionViewModel(ObserveSessionUseCase(repo))

        viewModel.session.test {
            assertThat(awaitItem()).isEqualTo(SessionState.Loading)
            assertThat(awaitItem()).isEqualTo(SessionState.Active(Role.CUSTOMER))
        }
    }

    @Test
    fun followsLogoutBackToSignedOut() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        repo.authState.value = AuthUser("u2", "u2@otli.test")
        repo.document.value = UserAccount("u2", Role.COURIER, AccountStatus.PENDING, "Luis", "u2@otli.test", "8888-1111")
        val viewModel = SessionViewModel(ObserveSessionUseCase(repo))

        viewModel.session.test {
            assertThat(awaitItem()).isEqualTo(SessionState.Loading)
            assertThat(awaitItem()).isEqualTo(SessionState.Pending(Role.COURIER))
            repo.logout()
            assertThat(awaitItem()).isEqualTo(SessionState.SignedOut)
        }
    }
}
