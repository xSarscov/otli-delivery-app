package com.otli.app.ordering.application

import com.otli.app.auth.application.AuthRepository
import com.otli.app.auth.domain.AuthUser
import com.otli.app.auth.domain.MerchantStoreDetails
import com.otli.app.auth.domain.ProfileFields
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An auth repository whose signed-in user is [authState]; the user documents are never needed. */
class FakeSignedInAuth(uid: String? = null) : AuthRepository {
    val authState = MutableStateFlow(uid?.let { AuthUser(it, "$it@otli.test") })

    override fun observeAuthState(): Flow<AuthUser?> = authState

    override fun observeUserDocument(uid: String): Flow<UserAccount?> = MutableStateFlow(null)

    override suspend fun register(email: String, password: String, role: Role, profileFields: ProfileFields, merchantStore: MerchantStoreDetails?) =
        Result.success(Unit)

    override suspend fun login(email: String, password: String) = Result.success(Unit)

    override suspend fun logout() {
        authState.value = null
    }
}
