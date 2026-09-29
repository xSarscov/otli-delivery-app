package com.otli.app.auth.domain

import com.otli.app.core.result.DomainError

sealed interface RegistrationDecision {
    data class Accepted(val status: AccountStatus) : RegistrationDecision
    data class Rejected(val error: DomainError) : RegistrationDecision
}

/**
 * Pure rule for the account status a self-registered user starts with.
 * The Firestore rules enforce the same table server-side; keep both in sync.
 */
object RegistrationPolicy {
    /** Roles offered by the public registration flow (Admin is never self-registrable). */
    val selfRegistrableRoles: List<Role> = listOf(Role.CUSTOMER, Role.MERCHANT, Role.COURIER)

    fun initialStatus(role: Role): RegistrationDecision = when (role) {
        Role.CUSTOMER -> RegistrationDecision.Accepted(AccountStatus.ACTIVE)
        Role.MERCHANT, Role.COURIER -> RegistrationDecision.Accepted(AccountStatus.PENDING)
        Role.ADMIN -> RegistrationDecision.Rejected(
            DomainError.Unauthorized("Admin accounts cannot be self-registered"),
        )
    }
}
