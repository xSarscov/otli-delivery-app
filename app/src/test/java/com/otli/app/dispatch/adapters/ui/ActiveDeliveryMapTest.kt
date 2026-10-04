package com.otli.app.dispatch.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.core.map.MapEmphasis
import com.otli.app.core.map.MapPin
import com.otli.app.core.map.MapScene
import com.otli.app.core.map.ReadOnlyMapContent
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The courier sees the store, the customer's address and their own position on one read-only map. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class ActiveDeliveryMapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val store = MapPin(12.2667, -86.5667)
    private val home = MapPin(12.27, -86.57)
    private val own = MapPin(12.268, -86.568)

    private val drawn = mutableListOf<MapScene>()
    private val map: ReadOnlyMapContent = { scene ->
        drawn += scene
        Text("delivery map slot")
    }

    private fun show(state: ActiveDeliveryUiState, ownPosition: MapPin? = null) {
        compose.setContent {
            ActiveDeliveryContent(
                state = state,
                onPickUp = {},
                onDeliver = {},
                onDismissError = {},
                deliveryMap = map,
                ownPosition = ownPosition,
            )
        }
    }

    private fun delivering(status: OrderStatus) =
        ActiveDeliveryUiState(isLoading = false, order = anOrder("o1", status, courierId = "courier-1"))

    @Test
    fun beforePickupTheMapShowsTheCourierTheStoreAndTheAddressWithTheStoreAsTheDestination() {
        show(delivering(OrderStatus.CLAIMED), ownPosition = own)

        compose.onNodeWithText("delivery map slot").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.map_attribution)).performScrollTo().assertIsDisplayed()
        assertThat(drawn.last()).isEqualTo(MapScene(dropoff = home, courier = own, pickup = store, emphasis = MapEmphasis.PICKUP))
    }

    @Test
    fun afterPickupTheAddressBecomesTheDestination() {
        show(delivering(OrderStatus.PICKED_UP), ownPosition = own)

        assertThat(drawn.last()).isEqualTo(MapScene(dropoff = home, courier = own, pickup = store, emphasis = MapEmphasis.DROPOFF))
    }

    @Test
    fun withoutAKnownPositionThePinsAreStillShownAndThereIsNoCourierMarker() {
        show(delivering(OrderStatus.CLAIMED))

        assertThat(drawn.last()).isEqualTo(MapScene(dropoff = home, courier = null, pickup = store, emphasis = MapEmphasis.PICKUP))
    }

    @Test
    fun theMapFollowsTheOrderTheCourierIsServing() {
        val order = anOrder("o2", OrderStatus.PICKED_UP, courierId = "courier-1")
        show(
            ActiveDeliveryUiState(
                isLoading = false,
                order = order.copy(
                    pickup = OrderLocation(12.2, -86.2, "Otra tienda"),
                    dropoff = order.dropoff.copy(latitude = 12.31, longitude = -86.6),
                ),
            ),
        )

        assertThat(drawn.last().dropoff).isEqualTo(MapPin(12.31, -86.6))
        assertThat(drawn.last().pickup).isEqualTo(MapPin(12.2, -86.2))
    }

    private fun legendText(role: Int, detail: String? = null) =
        if (detail == null) compose.activity.getString(role) else compose.activity.getString(R.string.map_legend_entry, compose.activity.getString(role), detail)

    @Test
    fun beforePickupALegendUnderTheMapNamesTheStoreAsTheDestinationTheAddressAndTheCourier() {
        show(delivering(OrderStatus.CLAIMED), ownPosition = own)

        compose.onNodeWithText(legendText(R.string.map_pickup_here_marker, "Frente al parque")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(legendText(R.string.map_dropoff_marker, "Casa azul")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(legendText(R.string.map_you_marker)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun afterPickupTheLegendNamesTheAddressAsTheDestinationAndTheStoreAsAPlace() {
        show(delivering(OrderStatus.PICKED_UP), ownPosition = own)

        compose.onNodeWithText(legendText(R.string.map_deliver_to_marker, "Casa azul")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(legendText(R.string.map_store_marker, "Frente al parque")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(legendText(R.string.map_pickup_here_marker, "Frente al parque")).assertDoesNotExist()
    }

    @Test
    fun theLegendDoesNotListTheCourierBeforeThePositionIsKnown() {
        show(delivering(OrderStatus.CLAIMED))

        compose.onNodeWithText(legendText(R.string.map_pickup_here_marker, "Frente al parque")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(legendText(R.string.map_you_marker)).assertDoesNotExist()
    }

    @Test
    fun withoutAnActiveOrderThereIsNoMap() {
        show(ActiveDeliveryUiState(isLoading = false))

        compose.onNodeWithText("delivery map slot").assertDoesNotExist()
        assertThat(drawn).isEmpty()
    }
}
