package com.otli.app.auth.adapters.ui

import com.otli.app.auth.application.AuthRepository
import com.otli.app.auth.domain.AuthUser
import com.otli.app.auth.domain.ProfileFields
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Records credential calls; the next outcome for each is configurable, and can be held open. */
class RecordingAuthRepository : AuthRepository {
    data class Registration(val email: String, val password: String, val role: Role, val profile: ProfileFields)

    val registrations = mutableListOf<Registration>()
    val logins = mutableListOf<Pair<String, String>>()
    var logoutCount = 0
    var registerOutcome: suspend () -> Result<Unit> = { Result.success(Unit) }
    var loginOutcome: suspend () -> Result<Unit> = { Result.success(Unit) }

    override fun observeAuthState(): Flow<AuthUser?> = emptyFlow()

    override fun observeUserDocument(uid: String): Flow<UserAccount?> = emptyFlow()

    override suspend fun register(
        email: String,
        password: String,
        role: Role,
        profileFields: ProfileFields,
    ): Result<Unit> {
        registrations += Registration(email, password, role, profileFields)
        return registerOutcome()
    }

    override suspend fun login(email: String, password: String): Result<Unit> {
        logins += email to password
        return loginOutcome()
    }

    override suspend fun logout() {
        logoutCount++
    }
}
