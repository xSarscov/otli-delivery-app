package com.otli.app.ordering.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class OrderTrackingContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private class Events {
        var cancels = 0
        var dismissedErrors = 0
        var backs = 0
    }

    private fun show(state: OrderTrackingUiState, events: Events = Events()): Events {
        compose.setContent {
            OrderTrackingContent(
                state = state,
                onCancel = { events.cancels++ },
                onDismissError = { events.dismissedErrors++ },
                onBack = { events.backs++ },
            )
        }
        return events
    }

    private fun tracking(order: Order) = OrderTrackingUiState(isLoading = false, order = order)

    private fun stepsWith(progress: Int) =
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(progress))).fetchSemanticsNodes().size

    @Test
    fun itHasATitleAndGoesBack() {
        val events = show(OrderTrackingUiState())

        compose.onNodeWithText(text(R.string.tracking_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        assertThat(events.backs).isEqualTo(1)
    }

    @Test
    fun itShowsProgressWhileLoading() {
        show(OrderTrackingUiState())

        compose.onNodeWithTag(OrderTrackingTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun aMissingOrderSaysSo() {
        show(OrderTrackingUiState(isLoading = false, notFound = true))

        compose.onNodeWithText(text(R.string.tracking_not_found)).assertIsDisplayed()
        compose.onNodeWithTag(OrderTrackingTags.LOADING).assertDoesNotExist()
    }

    @Test
    fun aLoadFailureSaysSo() {
        show(OrderTrackingUiState(isLoading = false, loadFailed = true))

        compose.onNodeWithText(text(R.string.tracking_load_failed)).assertIsDisplayed()
    }

    @Test
    fun theHeadlineNamesTheStoreAndTheCurrentStatus() {
        show(tracking(anOrder("o1", OrderStatus.PREPARING)))

        compose.onNodeWithText(text(R.string.cart_from_store, "Comedor Marta")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.order_status_preparing)).assertIsDisplayed()
    }

    @Test
    fun theTimelineMarksWhatIsDoneCurrentAndUpcoming() {
        show(tracking(anOrder("o1", OrderStatus.ACCEPTED)))

        assertThat(stepsWith(R.string.tracking_step_done)).isEqualTo(1)
        assertThat(stepsWith(R.string.tracking_step_current)).isEqualTo(1)
        assertThat(stepsWith(R.string.tracking_step_upcoming)).isEqualTo(5)
        compose.onNodeWithText(text(R.string.tracking_step_placed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tracking_step_delivered)).assertIsDisplayed()
    }

    @Test
    fun aRejectedOrderShowsTheReasonTheStoreGave() {
        show(tracking(anOrder("o1", OrderStatus.REJECTED, rejectReason = "No hay nacatamales")))

        compose.onNodeWithText(text(R.string.tracking_reject_reason, "No hay nacatamales")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tracking_step_rejected)).assertIsDisplayed()
    }

    @Test
    fun otherOrdersShowNoRejectionReason() {
        show(tracking(anOrder("o1", OrderStatus.ACCEPTED, rejectReason = "stale")))

        compose.onNodeWithText(text(R.string.tracking_reject_reason, "stale")).assertDoesNotExist()
    }

    @Test
    fun theOrderListsItsItemsTotalsAndDeliveryReference() {
        show(tracking(anOrder("o1", OrderStatus.PLACED)))

        compose.onNodeWithText("2 x Nacatamal, 1 x Fresco").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.price_nio, "265.00")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.price_nio, "30.00")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.price_nio, "295.00")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Casa azul").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cancelIsEnabledWhilePlacedAndReportsTheTap() {
        val events = show(tracking(anOrder("o1", OrderStatus.PLACED)))

        compose.onNodeWithText(text(R.string.tracking_cancel)).performScrollTo().assertIsEnabled().performClick()

        assertThat(events.cancels).isEqualTo(1)
        compose.onNodeWithText(text(R.string.tracking_cancel_hint)).assertDoesNotExist()
    }

    @Test
    fun cancelIsDisabledOnceTheStoreAnsweredAndExplainsWhy() {
        show(tracking(anOrder("o1", OrderStatus.ACCEPTED)))

        compose.onNodeWithText(text(R.string.tracking_cancel)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.tracking_cancel_hint)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cancelIsDisabledWhileTheCancellationIsInFlight() {
        show(tracking(anOrder("o1", OrderStatus.PLACED)).copy(isCancelling = true))

        compose.onNodeWithText(text(R.string.tracking_cancel)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun finishedOrdersOfferNoCancelAtAll() {
        show(tracking(anOrder("o1", OrderStatus.DELIVERED)))

        compose.onNodeWithText(text(R.string.tracking_cancel)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tracking_cancel_hint)).assertDoesNotExist()
    }

    @Test
    fun noErrorShowsWhenNothingFailed() {
        show(tracking(anOrder("o1", OrderStatus.PLACED)))

        compose.onNodeWithText(text(R.string.tracking_cancel_failed)).assertDoesNotExist()
    }

    @Test
    fun aFailedCancellationIsShownAndCanBeDismissed() {
        val events = show(tracking(anOrder("o1", OrderStatus.PLACED)).copy(error = TrackingError.CANCEL_FAILED))

        compose.onNodeWithText(text(R.string.tracking_cancel_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_dismiss)).performClick()

        assertThat(events.dismissedErrors).isEqualTo(1)
    }
}
