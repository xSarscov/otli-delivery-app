package com.otli.app.admin.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WaitingTimeTest {
    private val minute = 60_000L

    @Test
    fun itCountsWholeMinutesSincePlacement() {
        assertThat(WaitingTime.minutes(createdAtMillis = 1_000_000, nowMillis = 1_000_000 + 35 * minute)).isEqualTo(35)
        assertThat(WaitingTime.minutes(createdAtMillis = 1_000_000, nowMillis = 1_000_000 + 2 * minute + 59_999)).isEqualTo(2)
    }

    @Test
    fun lessThanAMinuteIsZero() {
        assertThat(WaitingTime.minutes(createdAtMillis = 5_000, nowMillis = 5_000)).isEqualTo(0)
        assertThat(WaitingTime.minutes(createdAtMillis = 5_000, nowMillis = 5_000 + minute - 1)).isEqualTo(0)
    }

    @Test
    fun aClockBehindThePlacementTimeNeverGoesNegative() {
        assertThat(WaitingTime.minutes(createdAtMillis = 9_000_000, nowMillis = 1_000_000)).isEqualTo(0)
    }

    @Test
    fun aPendingServerTimestampHasNoWaitingTimeYet() {
        assertThat(WaitingTime.minutes(createdAtMillis = 0, nowMillis = 10 * minute)).isNull()
    }
}
