package com.otli.app.tracking.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocationThrottleTest {
    private val start = GeoFix(latitude = 12.2656, longitude = -86.5664, accuracyMeters = 5f, timestampMillis = 1_000_000L)

    /** A fix [metersNorth] north of [start] taken [seconds] later. 1 degree of latitude is about 111.2 km. */
    private fun later(seconds: Long, metersNorth: Double) = start.copy(
        latitude = start.latitude + metersNorth / METERS_PER_DEGREE,
        timestampMillis = start.timestampMillis + seconds * 1000,
    )

    @Test
    fun theFirstFixAlwaysPublishes() {
        assertThat(LocationThrottle.shouldPublish(last = null, next = start)).isTrue()
    }

    @Test
    fun publishesWhenTenSecondsAndTenMetersHavePassed() {
        assertThat(LocationThrottle.shouldPublish(start, later(seconds = 10, metersNorth = 10.5))).isTrue()
        assertThat(LocationThrottle.shouldPublish(start, later(seconds = 25, metersNorth = 300.0))).isTrue()
    }

    @Test
    fun holdsBackWhenTheTimeIsEnoughButTheCourierBarelyMoved() {
        assertThat(LocationThrottle.shouldPublish(start, later(seconds = 10, metersNorth = 9.0))).isFalse()
        assertThat(LocationThrottle.shouldPublish(start, later(seconds = 59, metersNorth = 0.0))).isFalse()
    }

    @Test
    fun holdsBackWhenTheCourierMovedFarButTooSoon() {
        assertThat(LocationThrottle.shouldPublish(start, later(seconds = 9, metersNorth = 500.0))).isFalse()
        assertThat(LocationThrottle.shouldPublish(start, later(seconds = 0, metersNorth = 500.0))).isFalse()
    }

    @Test
    fun aStationaryCourierStillPublishesTheHeartbeatAfterSixtySeconds() {
        assertThat(LocationThrottle.shouldPublish(start, later(seconds = 60, metersNorth = 0.0))).isTrue()
        assertThat(LocationThrottle.shouldPublish(start, later(seconds = 120, metersNorth = 1.0))).isTrue()
    }

    @Test
    fun theThresholdsAreInclusive() {
        // Exactly 10 s publishes; one millisecond short of 10 s does not (the distance is just above 10 m).
        val farEnough = start.copy(latitude = start.latitude + 10.01 / METERS_PER_DEGREE)
        assertThat(LocationThrottle.shouldPublish(start, farEnough.copy(timestampMillis = start.timestampMillis + 10_000))).isTrue()
        assertThat(LocationThrottle.shouldPublish(start, farEnough.copy(timestampMillis = start.timestampMillis + 9_999))).isFalse()
        // Exactly the heartbeat publishes by time alone.
        assertThat(LocationThrottle.shouldPublish(start, start.copy(timestampMillis = start.timestampMillis + 60_000))).isTrue()
        assertThat(LocationThrottle.shouldPublish(start, start.copy(timestampMillis = start.timestampMillis + 59_999))).isFalse()
    }

    @Test
    fun aClockThatWentBackwardsNeverPublishesByElapsedTime() {
        assertThat(LocationThrottle.shouldPublish(start, later(seconds = -30, metersNorth = 500.0))).isFalse()
    }

    @Test
    fun distanceIsSymmetricAndZeroForTheSamePoint() {
        val other = later(seconds = 0, metersNorth = 250.0)
        assertThat(start.distanceMetersTo(start)).isEqualTo(0.0)
        assertThat(start.distanceMetersTo(other)).isWithin(0.5).of(250.0)
        assertThat(other.distanceMetersTo(start)).isWithin(0.5).of(250.0)
    }

    @Test
    fun distanceAlongALongitudeShrinksWithLatitude() {
        // 0.001 degrees of longitude at Nagarote (12.27 N) is 111.2 m * cos(12.27 deg), about 108.7 m.
        val east = start.copy(longitude = start.longitude + 0.001)
        assertThat(start.distanceMetersTo(east)).isWithin(0.5).of(108.7)
    }

    private companion object {
        const val METERS_PER_DEGREE = 111_195.0
    }
}
