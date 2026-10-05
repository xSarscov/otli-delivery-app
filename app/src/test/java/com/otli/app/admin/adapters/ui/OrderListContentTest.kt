package com.otli.app.admin.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.core.time.FixedClock
import com.otli.app.ordering.adapters.ui.PlacedAtFormatter
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class OrderListContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private val zone = ZoneId.of("America/Managua")
    private fun millisAt(hour: Int, minute: Int) = ZonedDateTime.of(2026, 9, 30, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    private val placedAtFormatter = PlacedAtFormatter(FixedClock(millisAt(15, 0)), zone, Locale.US)

    private fun show(state: OrderListUiState, opened: MutableList<String> = mutableListOf()): MutableList<String> {
        compose.setContent { OrderListContent(state = state, onOrderClick = { opened += it }, placedAt = placedAtFormatter) }
        return opened
    }

    private val newest = anOrder("o-new", OrderStatus.PLACED, merchantName = "Comedor Marta", createdAtMillis = millisAt(14, 30))
    private val oldest = anOrder("o-old", OrderStatus.DELIVERED, merchantName = "Pizzeria Chepe", createdAtMillis = millisAt(11, 5))

    private fun top(orderId: String) = compose.onNodeWithTag(OrderListTags.row(orderId)).fetchSemanticsNode().positionInRoot.y

    private fun rowHas(orderId: String, shown: String) =
        // The row is clickable, so its texts are merged into the row node itself.
        compose.onNode(hasTestTag(OrderListTags.row(orderId)) and hasText(shown)).assertIsDisplayed()

    @Test
    fun itShowsProgressWhileLoading() {
        show(OrderListUiState())

        compose.onNodeWithTag(OrderListTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun aFailedLoadSaysSo() {
        show(OrderListUiState(isLoading = false, loadFailed = true))

        compose.onNodeWithText(text(R.string.admin_orders_load_failed)).assertIsDisplayed()
    }

    @Test
    fun withNoOrdersItSaysSo() {
        show(OrderListUiState(isLoading = false))

        compose.onNodeWithText(text(R.string.admin_orders_empty)).assertIsDisplayed()
    }

    @Test
    fun theOrdersAreShownInTheOrderOfTheState() {
        show(OrderListUiState(isLoading = false, orders = listOf(newest, oldest)))

        assertThat(top("o-new")).isLessThan(top("o-old"))
        compose.onNodeWithText(text(R.string.admin_orders_empty)).assertDoesNotExist()
    }

    @Test
    fun anOrderShowsItsStoreCustomerStatusPlacementTimeAndTotal() {
        show(OrderListUiState(isLoading = false, orders = listOf(newest, oldest)))

        rowHas("o-new", "Comedor Marta")
        rowHas("o-new", text(R.string.admin_order_customer, "Ana Lopez"))
        rowHas("o-new", text(R.string.order_status_placed))
        rowHas("o-new", text(R.string.order_placed_at, "14:30"))
        rowHas("o-new", text(R.string.price_nio, "295.00"))
        rowHas("o-old", text(R.string.order_status_delivered))
    }

    @Test
    fun tappingAnOrderOpensIt() {
        val opened = show(OrderListUiState(isLoading = false, orders = listOf(newest, oldest)))

        compose.onNodeWithTag(OrderListTags.row("o-old")).performClick()

        assertThat(opened).containsExactly("o-old")
    }
}
