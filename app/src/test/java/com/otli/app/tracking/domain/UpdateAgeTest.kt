package com.otli.app.tracking.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateAgeTest {
    private val updatedAt = 1_700_000_000_000L

    @Test
    fun underAMinuteItCountsWholeSeconds() {
        assertThat(UpdateAge.of(nowMillis = updatedAt, updatedAtMillis = updatedAt)).isEqualTo(UpdateAge.Seconds(0))
        assertThat(UpdateAge.of(nowMillis = updatedAt + 7_400, updatedAtMillis = updatedAt)).isEqualTo(UpdateAge.Seconds(7))
        assertThat(UpdateAge.of(nowMillis = updatedAt + 59_999, updatedAtMillis = updatedAt)).isEqualTo(UpdateAge.Seconds(59))
    }

    @Test
    fun fromAMinuteOnItCountsWholeMinutes() {
        assertThat(UpdateAge.of(nowMillis = updatedAt + 60_000, updatedAtMillis = updatedAt)).isEqualTo(UpdateAge.Minutes(1))
        assertThat(UpdateAge.of(nowMillis = updatedAt + 125_000, updatedAtMillis = updatedAt)).isEqualTo(UpdateAge.Minutes(2))
    }

    @Test
    fun aServerClockAheadOfThePhoneIsNeverANegativeAge() {
        assertThat(UpdateAge.of(nowMillis = updatedAt - 3_000, updatedAtMillis = updatedAt)).isEqualTo(UpdateAge.Seconds(0))
    }
}
