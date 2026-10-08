package com.otli.app.admin.domain

/** How long an order has been waiting, for the Admin list; it is a hint, not a rule. */
object WaitingTime {
    private const val MILLIS_PER_MINUTE = 60_000L

    /** Whole minutes since [createdAtMillis], never negative; null while the server timestamp is still pending (zero). */
    fun minutes(createdAtMillis: Long, nowMillis: Long): Long? {
        if (createdAtMillis == 0L) return null
        return ((nowMillis - createdAtMillis) / MILLIS_PER_MINUTE).coerceAtLeast(0)
    }
}
