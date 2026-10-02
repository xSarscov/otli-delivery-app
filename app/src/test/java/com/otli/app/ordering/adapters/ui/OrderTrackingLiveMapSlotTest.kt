package com.otli.app.ordering.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tracking screen hosts the live map in a slot; the map itself decides whether it is visible. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class OrderTrackingLiveMapSlotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: OrderTrackingUiState) {
        compose.setContent {
            OrderTrackingContent(state = state, onCancel = {}, onDismissError = {}, onBack = {}, liveMap = { Text("live map slot") })
        }
    }

    @Test
    fun aLoadedOrderHostsTheLiveMapAmongItsDetails() {
        show(OrderTrackingUiState(isLoading = false, order = anOrder("o1", OrderStatus.PICKED_UP)))

        compose.onNodeWithText("live map slot").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withoutAnOrderThereIsNoRoomForAMap() {
        show(OrderTrackingUiState(isLoading = false, notFound = true))
        compose.onNodeWithText("live map slot").assertDoesNotExist()
    }

    @Test
    fun whileLoadingThereIsNoMapEither() {
        show(OrderTrackingUiState(isLoading = true))
        compose.onNodeWithText("live map slot").assertDoesNotExist()
    }
}
