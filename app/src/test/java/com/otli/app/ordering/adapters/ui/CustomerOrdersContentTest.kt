package com.otli.app.ordering.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
class CustomerOrdersContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private class Events {
        val opened = mutableListOf<String>()
        var backs = 0
    }

    private fun show(state: CustomerOrdersUiState, events: Events = Events()): Events {
        compose.setContent {
            CustomerOrdersContent(state = state, onOrderClick = { events.opened += it }, onBack = { events.backs++ })
        }
        return events
    }

    @Test
    fun itHasATitleAndGoesBack() {
        val events = show(CustomerOrdersUiState())

        compose.onNodeWithText(text(R.string.my_orders_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        assertThat(events.backs).isEqualTo(1)
    }

    @Test
    fun itShowsProgressWhileLoadingAndNotTheEmptyMessage() {
        show(CustomerOrdersUiState())

        compose.onNodeWithTag(CustomerOrdersTags.LOADING).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.my_orders_empty)).assertDoesNotExist()
    }

    @Test
    fun aCustomerWithoutOrdersIsToldSo() {
        show(CustomerOrdersUiState(isLoading = false))

        compose.onNodeWithText(text(R.string.my_orders_empty)).assertIsDisplayed()
        compose.onNodeWithTag(CustomerOrdersTags.LOADING).assertDoesNotExist()
    }

    @Test
    fun aLoadFailureSaysSoInsteadOfClaimingThereAreNoOrders() {
        show(CustomerOrdersUiState(isLoading = false, loadFailed = true))

        compose.onNodeWithText(text(R.string.my_orders_load_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.my_orders_empty)).assertDoesNotExist()
    }

    @Test
    fun activeAndPastOrdersAreGroupedUnderTheirOwnHeadings() {
        show(
            CustomerOrdersUiState(
                isLoading = false,
                active = listOf(anOrder("o1", OrderStatus.PREPARING)),
                past = listOf(anOrder("o2", OrderStatus.DELIVERED, merchantName = "Pulperia Sol")),
            ),
        )

        compose.onNodeWithText(text(R.string.my_orders_active)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.my_orders_past)).assertIsDisplayed()
        compose.onNodeWithText("Comedor Marta").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.order_status_preparing)).assertIsDisplayed()
        compose.onNodeWithText("Pulperia Sol").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.order_status_delivered)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.my_orders_empty)).assertDoesNotExist()
    }

    @Test
    fun aHeadingIsOnlyShownWhenItHasOrders() {
        show(CustomerOrdersUiState(isLoading = false, active = listOf(anOrder("o1", OrderStatus.PLACED))))

        compose.onNodeWithText(text(R.string.my_orders_active)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.my_orders_past)).assertDoesNotExist()
    }

    @Test
    fun onlyPastOrdersAreStillListedNotReportedAsEmpty() {
        show(CustomerOrdersUiState(isLoading = false, past = listOf(anOrder("o2", OrderStatus.CANCELLED))))

        compose.onNodeWithText(text(R.string.my_orders_past)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.my_orders_empty)).assertDoesNotExist()
    }

    @Test
    fun anOrderShowsItsItemsAndTotal() {
        show(CustomerOrdersUiState(isLoading = false, active = listOf(anOrder("o1", OrderStatus.PLACED))))

        compose.onNodeWithText("2 x Nacatamal, 1 x Fresco").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.price_nio, "295.00")).assertIsDisplayed()
    }

    @Test
    fun tappingAnOrderOpensItsTracking() {
        val events = show(
            CustomerOrdersUiState(
                isLoading = false,
                active = listOf(anOrder("o1", OrderStatus.PLACED)),
                past = listOf(anOrder("o2", OrderStatus.DELIVERED, merchantName = "Pulperia Sol")),
            ),
        )

        compose.onNodeWithText("Comedor Marta").performClick()
        compose.onNodeWithText("Pulperia Sol").performClick()

        assertThat(events.opened).containsExactly("o1", "o2").inOrder()
    }
}
