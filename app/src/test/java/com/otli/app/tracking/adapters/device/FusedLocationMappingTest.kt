package com.otli.app.tracking.adapters.device

import android.location.Location
import com.google.common.truth.Truth.assertThat
import com.otli.app.tracking.domain.GeoFix
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs under Robolectric because [Location] needs the Android runtime. */
@RunWith(RobolectricTestRunner::class)
class FusedLocationMappingTest {
    private fun location(latitude: Double, longitude: Double, accuracy: Float?, time: Long) = Location("fused").also {
        it.latitude = latitude
        it.longitude = longitude
        if (accuracy != null) it.accuracy = accuracy
        it.time = time
    }

    @Test
    fun aDeviceLocationBecomesAFix() {
        val fix = location(12.2656, -86.5664, accuracy = 6.5f, time = 1_700_000_000_000L).toGeoFix()

        assertThat(fix).isEqualTo(GeoFix(12.2656, -86.5664, accuracyMeters = 6.5f, timestampMillis = 1_700_000_000_000L))
    }

    @Test
    fun aLocationWithoutAccuracyIsReportedAsZeroMeters() {
        val fix = location(-0.5, 10.25, accuracy = null, time = 42L).toGeoFix()

        assertThat(fix.accuracyMeters).isEqualTo(0f)
        assertThat(fix.latitude).isEqualTo(-0.5)
        assertThat(fix.longitude).isEqualTo(10.25)
        assertThat(fix.timestampMillis).isEqualTo(42L)
    }
}
