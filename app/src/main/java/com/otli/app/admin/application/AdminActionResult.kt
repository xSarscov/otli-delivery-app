package com.otli.app.admin.application

/** Why an Admin action was refused before anything was written. */
enum class AdminRejection {
    /** Only merchants and couriers are approved or suspended. */
    ROLE_NOT_MANAGED,

    /** The account is not in a status this change starts from (approve an active one, suspend a pending one). */
    INVALID_STATUS_CHANGE,

    FEE_NOT_POSITIVE,
    ORDER_NOT_RELEASABLE,
    ORDER_NOT_CANCELLABLE,
    REASON_REQUIRED,
    REASON_TOO_LONG,
}

/** Outcome of an Admin use case. */
sealed interface AdminActionResult {
    data object Done : AdminActionResult

    /** Refused up front; nothing was written. */
    data class Rejected(val reason: AdminRejection) : AdminActionResult

    /** The write was attempted and failed (transport or rules). */
    data object Failed : AdminActionResult
}

internal fun Result<Unit>.toActionResult(): AdminActionResult =
    fold(onSuccess = { AdminActionResult.Done }, onFailure = { AdminActionResult.Failed })

internal fun rejected(reason: AdminRejection): AdminActionResult = AdminActionResult.Rejected(reason)
