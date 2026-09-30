package com.otli.app.auth.domain

/** Identity returned by the authentication provider (credentials only, no role). */
data class AuthUser(val uid: String, val email: String)

/** Profile fields supplied at registration; mirrors the editable `users/{uid}` fields. */
data class ProfileFields(val displayName: String, val phone: String)

/**
 * Store details a merchant supplies at registration; [phone] is already normalized
 * (`+505XXXXXXXX`). Written to `merchants/{uid}` in the same batch as `users/{uid}`.
 */
data class MerchantStoreDetails(val storeName: String, val phone: String, val latitude: Double, val longitude: Double)

/** The `users/{uid}` document: source of truth for role and status (ADR-6). */
data class UserAccount(
    val uid: String,
    val role: Role,
    val status: AccountStatus,
    val displayName: String,
    val email: String,
    val phone: String,
)
