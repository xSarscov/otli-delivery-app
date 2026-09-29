package com.otli.app.auth.domain

/** Identity returned by the authentication provider (credentials only, no role). */
data class AuthUser(val uid: String, val email: String)

/** Profile fields supplied at registration; mirrors the editable `users/{uid}` fields. */
data class ProfileFields(val displayName: String, val phone: String)

/** The `users/{uid}` document: source of truth for role and status (ADR-6). */
data class UserAccount(
    val uid: String,
    val role: Role,
    val status: AccountStatus,
    val displayName: String,
    val email: String,
    val phone: String,
)
