package com.otli.app.auth.application

import com.otli.app.auth.domain.AuthUser
import com.otli.app.auth.domain.MerchantStoreDetails
import com.otli.app.auth.domain.ProfileFields
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import kotlinx.coroutines.flow.Flow

/** Port over the credentials provider and the `users/{uid}` document. */
interface AuthRepository {
    /** Emits the signed-in identity, or null when signed out. */
    fun observeAuthState(): Flow<AuthUser?>

    /** Emits the user document, or null while it does not exist yet. */
    fun observeUserDocument(uid: String): Flow<UserAccount?>

    /**
     * Creates the credential and `users/{uid}`. A [Role.MERCHANT] registration must carry
     * [merchantStore]; the merchant profile is created in the same batch.
     */
    suspend fun register(
        email: String,
        password: String,
        role: Role,
        profileFields: ProfileFields,
        merchantStore: MerchantStoreDetails? = null,
    ): Result<Unit>

    suspend fun login(email: String, password: String): Result<Unit>

    suspend fun logout()
}
