package com.otli.app.auth.application

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.AuthUser
import com.otli.app.auth.domain.MerchantStoreDetails
import com.otli.app.auth.domain.ProfileFields
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.SessionState
import com.otli.app.auth.domain.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeAuthRepository : AuthRepository {
    val authState = MutableStateFlow<AuthUser?>(null)
    private val documents = mutableMapOf<String, MutableStateFlow<UserAccount?>>()

    fun document(uid: String): MutableStateFlow<UserAccount?> =
        documents.getOrPut(uid) { MutableStateFlow(null) }

    override fun observeAuthState(): Flow<AuthUser?> = authState
    override fun observeUserDocument(uid: String): Flow<UserAccount?> = document(uid)
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

private fun account(uid: String, role: Role, status: AccountStatus) =
    UserAccount(uid, role, status, displayName = "Name $uid", email = "$uid@otli.test", phone = "8888-0000")

class ObserveSessionUseCaseTest {
    private val repo = FakeAuthRepository()
    private val useCase = ObserveSessionUseCase(repo)

    @Test
    fun signedOutWhenThereIsNoAuthUser() = runTest {
        useCase().test {
            assertThat(awaitItem()).isEqualTo(SessionState.Loading)
            assertThat(awaitItem()).isEqualTo(SessionState.SignedOut)
        }
    }

    @Test
    fun profileIncompleteWhenSignedInWithoutUserDocument() = runTest {
        repo.authState.value = AuthUser("u1", "u1@otli.test")
        useCase().test {
            assertThat(awaitItem()).isEqualTo(SessionState.Loading)
            assertThat(awaitItem()).isEqualTo(SessionState.ProfileIncomplete)
        }
    }

    @Test
    fun pendingMerchantAndCourierMapToPendingWithRole() = runTest {
        for (role in listOf(Role.MERCHANT, Role.COURIER)) {
            repo.authState.value = AuthUser("p-$role", "p@otli.test")
            repo.document("p-$role").value = account("p-$role", role, AccountStatus.PENDING)
            useCase().test {
                assertThat(awaitItem()).isEqualTo(SessionState.Loading)
                assertThat(awaitItem()).isEqualTo(SessionState.Pending(role))
            }
        }
    }

    @Test
    fun suspendedMapsToSuspendedWithRole() = runTest {
        repo.authState.value = AuthUser("s1", "s1@otli.test")
        repo.document("s1").value = account("s1", Role.COURIER, AccountStatus.SUSPENDED)
        useCase().test {
            assertThat(awaitItem()).isEqualTo(SessionState.Loading)
            assertThat(awaitItem()).isEqualTo(SessionState.Suspended(Role.COURIER))
        }
    }

    @Test
    fun activeAccountOfEachRoleReachesItsOwnRole() = runTest {
        for (role in Role.entries) {
            repo.authState.value = AuthUser("a-$role", "a@otli.test")
            repo.document("a-$role").value = account("a-$role", role, AccountStatus.ACTIVE)
            useCase().test {
                assertThat(awaitItem()).isEqualTo(SessionState.Loading)
                assertThat(awaitItem()).isEqualTo(SessionState.Active(role))
            }
        }
    }

    @Test
    fun reroutesLiveWhenAdminSuspendsTheAccountAndWhenUserLogsOut() = runTest {
        repo.authState.value = AuthUser("m1", "m1@otli.test")
        repo.document("m1").value = account("m1", Role.MERCHANT, AccountStatus.ACTIVE)
        useCase().test {
            assertThat(awaitItem()).isEqualTo(SessionState.Loading)
            assertThat(awaitItem()).isEqualTo(SessionState.Active(Role.MERCHANT))

            repo.document("m1").value = account("m1", Role.MERCHANT, AccountStatus.SUSPENDED)
            assertThat(awaitItem()).isEqualTo(SessionState.Suspended(Role.MERCHANT))

            repo.logout()
            assertThat(awaitItem()).isEqualTo(SessionState.SignedOut)
        }
    }
}
