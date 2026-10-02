package com.otli.app.dispatch.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.core.map.MapPin
import com.otli.app.core.map.ReadOnlyMapContent
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The courier sees the dropoff pin on a read-only map, not only as coordinates. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class ActiveDeliveryMapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val drawn = mutableListOf<Pair<MapPin, MapPin?>>()
    private val map: ReadOnlyMapContent = { dropoff, courier ->
        drawn += dropoff to courier
        Text("dropoff map slot")
    }

    private fun show(state: ActiveDeliveryUiState) {
        compose.setContent {
            ActiveDeliveryContent(state = state, onPickUp = {}, onDeliver = {}, onDismissError = {}, dropoffMap = map)
        }
    }

    @Test
    fun aClaimedOrderShowsItsDropoffPinOnTheMapWithTheAttribution() {
        show(ActiveDeliveryUiState(isLoading = false, order = anOrder("o1", OrderStatus.CLAIMED, courierId = "courier-1")))

        compose.onNodeWithText("dropoff map slot").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.map_attribution)).performScrollTo().assertIsDisplayed()
        assertThat(drawn.last()).isEqualTo(MapPin(12.27, -86.57) to null)
    }

    @Test
    fun theMapFollowsTheOrderTheCourierIsServing() {
        val order = anOrder("o2", OrderStatus.PICKED_UP, courierId = "courier-1")
        show(ActiveDeliveryUiState(isLoading = false, order = order.copy(dropoff = order.dropoff.copy(latitude = 12.31, longitude = -86.6))))

        assertThat(drawn.last()).isEqualTo(MapPin(12.31, -86.6) to null)
    }

    @Test
    fun withoutAnActiveOrderThereIsNoMap() {
        show(ActiveDeliveryUiState(isLoading = false))

        compose.onNodeWithText("dropoff map slot").assertDoesNotExist()
        assertThat(drawn).isEmpty()
    }
}
