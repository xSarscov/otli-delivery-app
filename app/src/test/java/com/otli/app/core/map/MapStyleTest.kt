package com.otli.app.core.map

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs under Robolectric because `org.json` and Compose need the Android runtime. */
@RunWith(RobolectricTestRunner::class)
class MapStyleTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theVectorStyleIsTheKeylessOpenFreeMapLibertyStyleOverHttps() {
        assertThat(MapStyle.VECTOR_URL).isEqualTo("https://tiles.openfreemap.org/styles/liberty")
    }

    @Test
    fun theFallbackIsAValidStyleWithOneOpenStreetMapRasterLayer() {
        val style = JSONObject(MapStyle.RASTER_FALLBACK_JSON)

        assertThat(style.getInt("version")).isEqualTo(8)
        val source = style.getJSONObject("sources").getJSONObject("osm")
        assertThat(source.getString("type")).isEqualTo("raster")
        assertThat(source.getJSONArray("tiles").getString(0)).isEqualTo("https://tile.openstreetmap.org/{z}/{x}/{y}.png")
        val layers = style.getJSONArray("layers")
        assertThat(layers.length()).isEqualTo(1)
        assertThat(layers.getJSONObject(0).getString("source")).isEqualTo("osm")
        assertThat(layers.getJSONObject(0).getString("type")).isEqualTo("raster")
    }

    @Test
    fun theFallbackCreditsOpenStreetMapContributors() {
        val attribution = JSONObject(MapStyle.RASTER_FALLBACK_JSON).getJSONObject("sources").getJSONObject("osm").getString("attribution")

        assertThat(attribution).contains("OpenStreetMap contributors")
    }

    @Test
    fun theAttributionTheTileLicencesRequireIsShownUnderEveryMap() {
        compose.setContent { MapAttribution() }

        compose.onNodeWithText(compose.activity.getString(R.string.map_attribution)).assertIsDisplayed()
        assertThat(compose.activity.getString(R.string.map_attribution)).contains("OpenFreeMap")
        assertThat(compose.activity.getString(R.string.map_attribution)).contains("OpenStreetMap contributors")
    }
}
