package com.otli.app.core.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.maplibre.android.annotations.Marker
import org.maplibre.android.geometry.LatLng

/**
 * MapLibre draws a marker's info window natively, above the Compose tree and outside the map's bounds, so
 * it floats over the rest of a scrolling screen. None of our maps may open one: markers are created without
 * a title or snippet and a tap on a marker is consumed.
 */
@RunWith(RobolectricTestRunner::class)
@Suppress("DEPRECATION")
class MapMarkersTest {

    @Test
    fun aMarkerIsCreatedAtThePinWithoutTitleOrSnippet() {
        val options = markerOptions(MapPin(12.27, -86.57))

        assertThat(options.position).isEqualTo(LatLng(12.27, -86.57))
        assertThat(options.title).isNull()
        assertThat(options.snippet).isNull()
    }

    @Test
    fun aDifferentPinGivesADifferentPositionAgainWithoutTitleOrSnippet() {
        val options = markerOptions(MapPin(12.2667, -86.5667))

        assertThat(options.position).isEqualTo(LatLng(12.2667, -86.5667))
        assertThat(options.title).isNull()
        assertThat(options.snippet).isNull()
    }

    @Test
    fun aTapOnAMarkerIsConsumedSoNoInfoWindowOpens() {
        val marker: Marker = markerOptions(MapPin(12.27, -86.57)).marker

        assertThat(ConsumeMarkerClicks.onMarkerClick(marker)).isTrue()
    }
}
