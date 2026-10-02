package com.otli.app.tracking.domain

/** How long ago the courier's position was published, for the "updated N s ago" line. */
sealed interface UpdateAge {
    data class Seconds(val value: Long) : UpdateAge

    data class Minutes(val value: Long) : UpdateAge

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L
        private const val SECONDS_PER_MINUTE = 60L

        /** [updatedAtMillis] is the server's clock; a phone clock behind it never yields a negative age. */
        fun of(nowMillis: Long, updatedAtMillis: Long): UpdateAge {
            val seconds = maxOf(0L, (nowMillis - updatedAtMillis) / MILLIS_PER_SECOND)
            return if (seconds < SECONDS_PER_MINUTE) Seconds(seconds) else Minutes(seconds / SECONDS_PER_MINUTE)
        }
    }
}
