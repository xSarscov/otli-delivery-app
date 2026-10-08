package com.otli.app.admin.application

import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import javax.inject.Inject

private fun UserAccount.isManaged() = role == Role.MERCHANT || role == Role.COURIER

/**
 * Lets a merchant or courier in: a `pending` account is approved and a `suspended` one is
 * reactivated, both ending `active`. An already active account has nothing to approve.
 */
class ApproveAccount @Inject constructor(private val admin: AdminRepository) {
    suspend operator fun invoke(account: UserAccount): AdminActionResult = when {
        !account.isManaged() -> rejected(AdminRejection.ROLE_NOT_MANAGED)
        account.status == AccountStatus.ACTIVE -> rejected(AdminRejection.INVALID_STATUS_CHANGE)
        else -> admin.setAccountStatus(account.uid, account.role, AccountStatus.ACTIVE).toActionResult()
    }
}

/** Blocks an `active` merchant or courier; the rules refuse everything that account does from then on. */
class SuspendAccount @Inject constructor(private val admin: AdminRepository) {
    suspend operator fun invoke(account: UserAccount): AdminActionResult = when {
        !account.isManaged() -> rejected(AdminRejection.ROLE_NOT_MANAGED)
        account.status != AccountStatus.ACTIVE -> rejected(AdminRejection.INVALID_STATUS_CHANGE)
        else -> admin.setAccountStatus(account.uid, account.role, AccountStatus.SUSPENDED).toActionResult()
    }
}
