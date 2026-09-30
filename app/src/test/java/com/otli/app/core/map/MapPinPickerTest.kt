package com.otli.app.core.map

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The MapLibre view is replaced by a fake slot: native rendering is verified on a device. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class MapPinPickerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun show(pin: MapPin?, onPinChange: (MapPin?) -> Unit = {}, tapAt: Pair<Double, Double> = 12.3 to -86.6) {
        compose.setContent {
            MapPinPicker(
                pin = pin,
                onPinChange = onPinChange,
                mapContent = { _, onTap ->
                    Box(Modifier.testTag("fake-map").clickable { onTap(tapAt.first, tapAt.second) }) { Text("map") }
                },
            )
        }
    }

    @Test
    fun theOpenFreeMapAttributionIsAlwaysVisible() {
        show(pin = null)

        compose.onNodeWithText(
            "OpenFreeMap © OpenMapTiles, data © OpenStreetMap contributors",
        ).assertIsDisplayed()
        assertThat(text(R.string.map_attribution)).contains("OpenStreetMap contributors")
    }

    @Test
    fun withoutAPinItShowsTheHintAndNoClearButton() {
        show(pin = null)

        compose.onNodeWithText(text(R.string.map_pin_hint)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.map_pin_clear)).assertDoesNotExist()
    }

    @Test
    fun withAPinItShowsTheCoordinatesAndAClearButton() {
        show(pin = MapPin(12.2656, -86.5664))

        compose.onNodeWithText("12.26560, -86.56640", substring = true).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.map_pin_clear)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.map_pin_hint)).assertDoesNotExist()
    }

    @Test
    fun tappingTheMapReportsTheTappedPin() {
        var changed: MapPin? = null
        show(pin = null, onPinChange = { changed = it }, tapAt = 12.31 to -86.61)

        compose.onNodeWithTag("fake-map").performClick()

        assertThat(changed).isEqualTo(MapPin(12.31, -86.61))
    }

    @Test
    fun aTapOutsideTheValidRangeIsNotReported() {
        var calls = 0
        show(pin = null, onPinChange = { calls++ }, tapAt = 95.0 to 0.0)

        compose.onNodeWithTag("fake-map").performClick()

        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun anInvalidTapKeepsAnExistingPinWithoutReportingIt() {
        var calls = 0
        show(pin = MapPin(12.3, -86.6), onPinChange = { calls++ }, tapAt = 95.0 to 0.0)

        compose.onNodeWithTag("fake-map").performClick()

        assertThat(calls).isEqualTo(0)
    }

    @Test
    fun clearingReportsANullPin() {
        var changed: MapPin? = MapPin(1.0, 1.0)
        show(pin = MapPin(12.3, -86.6), onPinChange = { changed = it })

        compose.onNodeWithText(text(R.string.map_pin_clear)).performClick()

        assertThat(changed).isNull()
    }
}
