package com.otli.app.tracking.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.core.map.MapPin
import com.otli.app.core.map.MapScene
import com.otli.app.core.map.ReadOnlyMapContent
import com.otli.app.tracking.domain.LivePosition
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveMapContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private val dropoff = MapPin(12.27, -86.57)
    private val courierAt = LivePosition("courier-1", 12.2656, -86.5664, accuracyMeters = 6f, updatedAtMillis = 1_000_000L)

    /** Records what the map was asked to draw instead of rendering MapLibre, which needs a device. */
    private class RecordingMap {
        val drawn = mutableListOf<MapScene>()
        val content: ReadOnlyMapContent = { scene ->
            drawn += scene
            Text("map slot")
        }
    }

    private fun show(state: LiveMapUiState, nowMillis: Long = 1_000_000L): RecordingMap {
        val map = RecordingMap()
        compose.setContent { LiveMapContent(state = state, nowMillis = nowMillis, mapContent = map.content) }
        return map
    }

    private fun following(courier: LivePosition? = null, locationUnavailable: Boolean = false) =
        LiveMapUiState(isLoading = false, visible = true, dropoff = dropoff, courier = courier, locationUnavailable = locationUnavailable)

    @Test
    fun aHiddenMapShowsNothingAtAll() {
        val map = show(LiveMapUiState.Hidden)

        compose.onNodeWithText(text(R.string.live_map_title)).assertDoesNotExist()
        compose.onNodeWithText("map slot").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.map_attribution)).assertDoesNotExist()
        assertThat(map.drawn).isEmpty()
    }

    @Test
    fun aViewerWhoMayNotFollowTheCourierSeesNothingEvenIfThePositionIsAtHand() {
        val map = show(LiveMapUiState(isLoading = false, visible = false, dropoff = dropoff, courier = courierAt))

        compose.onNodeWithText("map slot").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.live_map_title)).assertDoesNotExist()
        assertThat(map.drawn).isEmpty()
    }

    @Test
    fun aMapStillLoadingShowsNothing() {
        val map = show(LiveMapUiState(isLoading = true))

        compose.onNodeWithText("map slot").assertDoesNotExist()
        assertThat(map.drawn).isEmpty()
    }

    @Test
    fun beforeTheCourierPublishesTheMapShowsTheDropoffAndSaysItIsWaiting() {
        val map = show(following(courier = null))

        compose.onNodeWithText(text(R.string.live_map_title)).assertIsDisplayed()
        compose.onNodeWithText("map slot").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.live_map_waiting)).assertIsDisplayed()
        assertThat(map.drawn.last()).isEqualTo(MapScene(dropoff))
    }

    @Test
    fun theCourierMarkerIsDrawnAtTheLivePosition() {
        val map = show(following(courier = courierAt))

        assertThat(map.drawn.last()).isEqualTo(MapScene(dropoff, courier = MapPin(12.2656, -86.5664)))
        compose.onNodeWithText(text(R.string.live_map_waiting)).assertDoesNotExist()
    }

    @Test
    fun underAMinuteTheLastUpdateIsShownInSeconds() {
        show(following(courier = courierAt), nowMillis = 1_007_400L)
        compose.onNodeWithText(text(R.string.live_map_updated_seconds, 7)).assertIsDisplayed()
    }

    @Test
    fun overAMinuteTheLastUpdateIsShownInMinutes() {
        show(following(courier = courierAt), nowMillis = 1_000_000L + 125_000L)

        compose.onNodeWithText(text(R.string.live_map_updated_minutes, 2)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.live_map_updated_seconds, 125)).assertDoesNotExist()
    }

    @Test
    fun theTileAttributionIsShownUnderTheMap() {
        show(following(courier = courierAt))

        compose.onNodeWithText(text(R.string.map_attribution)).assertIsDisplayed()
    }

    @Test
    fun aLegendUnderTheMapNamesTheAddressAndTheCourierOnceItIsKnown() {
        show(following(courier = courierAt))

        compose.onNodeWithText(text(R.string.map_dropoff_marker)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.map_courier_marker)).assertIsDisplayed()
    }

    @Test
    fun theLegendHasNoCourierEntryBeforeTheCourierPublishes() {
        show(following(courier = null))

        compose.onNodeWithText(text(R.string.map_dropoff_marker)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.map_courier_marker)).assertDoesNotExist()
    }

    @Test
    fun anUnavailableLocationKeepsTheMapWithTheDropoffAndSaysSo() {
        val map = show(following(courier = null, locationUnavailable = true))

        compose.onNodeWithText(text(R.string.live_map_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.live_map_waiting)).assertDoesNotExist()
        assertThat(map.drawn.last()).isEqualTo(MapScene(dropoff))
    }
}
