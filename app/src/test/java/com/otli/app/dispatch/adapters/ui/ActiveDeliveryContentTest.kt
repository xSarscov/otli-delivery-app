package com.otli.app.dispatch.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class ActiveDeliveryContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private class Events {
        var pickedUp = 0
        var delivered = 0
        var dismissed = 0
    }

    private fun show(state: ActiveDeliveryUiState): Events {
        val events = Events()
        compose.setContent {
            ActiveDeliveryContent(
                state = state,
                onPickUp = { events.pickedUp++ },
                onDeliver = { events.delivered++ },
                onDismissError = { events.dismissed++ },
            )
        }
        return events
    }

    private fun active(status: OrderStatus) =
        ActiveDeliveryUiState(isLoading = false, order = anOrder("o1", status, courierId = "courier-1"))

    @Test
    fun itShowsProgressWhileLoading() {
        show(ActiveDeliveryUiState())

        compose.onNodeWithTag(DeliveryTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun withoutAnActiveOrderNothingIsShown() {
        show(ActiveDeliveryUiState(isLoading = false))

        compose.onNodeWithText(text(R.string.delivery_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.delivery_pick_up)).assertDoesNotExist()
        compose.onNodeWithTag(DeliveryTags.LOADING).assertDoesNotExist()
    }

    @Test
    fun aClaimedOrderShowsTheStoreTheDropoffAndTheCustomerToTakeItTo() {
        show(active(OrderStatus.CLAIMED))

        compose.onNodeWithText(text(R.string.delivery_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.delivery_step_claimed)).assertIsDisplayed()
        compose.onNodeWithText("Comedor Marta").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.delivery_pickup, "Frente al parque")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.delivery_dropoff, "Casa azul")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.delivery_pin, "12.27000, -86.57000")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.delivery_customer, "Ana Lopez", "+50588880201")).assertIsDisplayed()
        compose.onNodeWithText("2 x Nacatamal, 1 x Fresco").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.delivery_cash, text(R.string.price_nio, "295.00"))).assertIsDisplayed()
    }

    @Test
    fun onlyPickUpIsEnabledWhileTheOrderIsClaimed() {
        val events = show(active(OrderStatus.CLAIMED))

        compose.onNodeWithText(text(R.string.delivery_deliver)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.delivery_pick_up)).assertIsEnabled().performClick()

        assertThat(events.pickedUp).isEqualTo(1)
        assertThat(events.delivered).isEqualTo(0)
    }

    @Test
    fun onlyDeliverIsEnabledOnceTheOrderIsPickedUp() {
        val events = show(active(OrderStatus.PICKED_UP))

        compose.onNodeWithText(text(R.string.delivery_step_picked_up)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.delivery_step_claimed)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.delivery_pick_up)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.delivery_deliver)).assertIsEnabled().performClick()

        assertThat(events.delivered).isEqualTo(1)
        assertThat(events.pickedUp).isEqualTo(0)
    }

    @Test
    fun bothButtonsAreLockedWhileAStepIsInFlight() {
        show(active(OrderStatus.CLAIMED).copy(isBusy = true))

        compose.onNodeWithText(text(R.string.delivery_pick_up)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.delivery_deliver)).assertIsNotEnabled()
    }

    @Test
    fun anActionErrorIsShownAndCanBeDismissed() {
        val events = show(active(OrderStatus.CLAIMED).copy(error = DeliveryError.ACTION_FAILED))

        compose.onNodeWithText(text(R.string.delivery_error_action)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.delivery_error_load)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_dismiss)).performClick()

        assertThat(events.dismissed).isEqualTo(1)
    }

    @Test
    fun aLoadErrorIsShownEvenWithoutAnOrder() {
        show(ActiveDeliveryUiState(isLoading = false, error = DeliveryError.LOAD_FAILED))

        compose.onNodeWithText(text(R.string.delivery_error_load)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.delivery_error_action)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.delivery_pick_up)).assertDoesNotExist()
    }
}
