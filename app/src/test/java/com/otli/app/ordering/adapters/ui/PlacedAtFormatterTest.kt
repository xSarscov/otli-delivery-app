package com.otli.app.ordering.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.time.FixedClock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Test

class PlacedAtFormatterTest {
    private val zone = ZoneId.of("America/Managua")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private val now = at(2026, 9, 30, 15, 0)
    private val formatter = PlacedAtFormatter(FixedClock(now), zone, Locale.US)

    @Test
    fun anOrderPlacedTodayShowsOnlyItsLocalTime() {
        assertThat(formatter.format(at(2026, 9, 30, 11, 4))).isEqualTo(PlacedAt(time = "11:04", date = null))
        assertThat(formatter.format(at(2026, 9, 30, 0, 5))).isEqualTo(PlacedAt(time = "00:05", date = null))
    }

    @Test
    fun anOrderFromAnotherDayAlsoShowsItsDate() {
        assertThat(formatter.format(at(2026, 9, 29, 23, 59))).isEqualTo(PlacedAt(time = "23:59", date = "29 Sep"))
        assertThat(formatter.format(at(2026, 8, 3, 8, 30))).isEqualTo(PlacedAt(time = "08:30", date = "3 Aug"))
    }

    @Test
    fun theDayIsJudgedInTheDevicesTimeZoneNotUtc() {
        // 03:00 UTC on Oct 1 is 21:00 on Sep 30 in Managua, so it is still "today".
        val lateTonight = ZonedDateTime.of(2026, 10, 1, 3, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()

        assertThat(formatter.format(lateTonight)).isEqualTo(PlacedAt(time = "21:00", date = null))
    }

    @Test
    fun aPendingServerTimestampHasNoTimeYet() {
        assertThat(formatter.format(0L)).isNull()
    }
}
