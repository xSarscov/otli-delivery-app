package com.otli.app.ordering.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the customer sees while a courier works the order (Slice 4): the courier states are not just
 * labels in a table, they render on the tracking screen and the timeline reaches `delivered`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class OrderTrackingCourierStatusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun show(status: OrderStatus) {
        compose.setContent {
            OrderTrackingContent(
                state = OrderTrackingUiState(isLoading = false, order = anOrder("o1", status, courierId = "courier-1")),
                onCancel = {},
                onDismissError = {},
                onBack = {},
            )
        }
    }

    private fun stepsWith(progress: Int) =
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(progress))).fetchSemanticsNodes().size

    @Test
    fun aClaimedOrderTellsTheCustomerACourierIsOnTheWayToTheStore() {
        show(OrderStatus.CLAIMED)

        compose.onNodeWithText(text(R.string.order_status_claimed)).assertIsDisplayed()
        assertThat(stepsWith(R.string.tracking_step_done)).isEqualTo(4)
        assertThat(stepsWith(R.string.tracking_step_current)).isEqualTo(1)
        assertThat(stepsWith(R.string.tracking_step_upcoming)).isEqualTo(2)
    }

    @Test
    fun aPickedUpOrderTellsTheCustomerItIsOnItsWay() {
        show(OrderStatus.PICKED_UP)

        compose.onNodeWithText(text(R.string.order_status_picked_up)).assertIsDisplayed()
        assertThat(stepsWith(R.string.tracking_step_done)).isEqualTo(5)
        assertThat(stepsWith(R.string.tracking_step_upcoming)).isEqualTo(1)
    }

    @Test
    fun aDeliveredOrderCompletesTheTimeline() {
        show(OrderStatus.DELIVERED)

        // The headline and the last timeline step both read "Delivered".
        compose.onAllNodesWithText(text(R.string.order_status_delivered)).assertCountEquals(2)
        assertThat(stepsWith(R.string.tracking_step_done)).isEqualTo(6)
        assertThat(stepsWith(R.string.tracking_step_current)).isEqualTo(1)
        assertThat(stepsWith(R.string.tracking_step_upcoming)).isEqualTo(0)
    }
}
