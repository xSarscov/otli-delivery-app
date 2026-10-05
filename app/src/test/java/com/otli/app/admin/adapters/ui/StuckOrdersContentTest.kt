package com.otli.app.admin.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
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
import com.otli.app.ordering.domain.Order
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
class StuckOrdersContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private val zone = ZoneId.of("America/Managua")
    private fun millisAt(hour: Int, minute: Int) = ZonedDateTime.of(2026, 9, 30, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
    private val now = millisAt(15, 0)
    private val placedAtFormatter = PlacedAtFormatter(FixedClock(now), zone, Locale.US)

    private class Events {
        val released = mutableListOf<String>()
        val cancelStarted = mutableListOf<String>()
        val confirmed = mutableListOf<String>()
        var dismissedCancel = 0
        var dismissedError = 0
    }

    /** A stand-in for the real dialog (Robolectric cannot host a text field in a dialog): it reports what it was given. */
    private fun show(state: StuckOrdersUiState, events: Events = Events()): Events {
        compose.setContent {
            StuckOrdersContent(
                state = state,
                nowMillis = now,
                onRelease = { events.released += it.id },
                onCancel = { events.cancelStarted += it.id },
                onConfirmCancel = { events.confirmed += it },
                onDismissCancel = { events.dismissedCancel++ },
                onDismissError = { events.dismissedError++ },
                placedAt = placedAtFormatter,
                cancelDialog = { dialog ->
                    Column {
                        Text("dialog-for-${dialog.order.id}-invalid=${dialog.reasonInvalid}-busy=${dialog.busy}")
                        TextButton(onClick = { dialog.onConfirm("typed reason") }) { Text("dialog-confirm") }
                        TextButton(onClick = dialog.onDismiss) { Text("dialog-dismiss") }
                    }
                },
            )
        }
        return events
    }

    private val waiting = anOrder("w1", OrderStatus.READY, merchantName = "Comedor Marta", createdAtMillis = millisAt(14, 25))
    private val withCourier = anOrder("c1", OrderStatus.CLAIMED, merchantName = "Pizzeria Chepe", createdAtMillis = millisAt(14, 40), courierId = "courier-1")
    private val inKitchen = anOrder("k1", OrderStatus.PREPARING, merchantName = "Fritanga Lola", createdAtMillis = millisAt(14, 50))

    private val everything = StuckOrdersUiState(
        isLoading = false,
        waiting = listOf(waiting),
        withCourier = listOf(withCourier),
        inKitchen = listOf(inKitchen),
    )

    private fun row(order: Order) = hasTestTag(StuckOrdersTags.row(order.id))

    @Test
    fun itShowsProgressWhileLoading() {
        show(StuckOrdersUiState())

        compose.onNodeWithTag(StuckOrdersTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun aFailedLoadSaysSo() {
        show(StuckOrdersUiState(isLoading = false, loadFailed = true))

        compose.onNodeWithText(text(R.string.admin_stuck_load_failed)).assertIsDisplayed()
    }

    @Test
    fun withNothingToActOnItSaysSoAndShowsNoSection() {
        show(StuckOrdersUiState(isLoading = false))

        compose.onNodeWithText(text(R.string.admin_stuck_empty)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_stuck_waiting)).assertDoesNotExist()
    }

    @Test
    fun eachGroupHasItsTitleAndItsOrders() {
        show(everything)

        compose.onNodeWithText(text(R.string.admin_stuck_waiting)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_stuck_with_courier)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_stuck_kitchen)).assertIsDisplayed()
        compose.onNodeWithText("Comedor Marta").assertIsDisplayed()
        compose.onNodeWithText("Pizzeria Chepe").assertIsDisplayed()
        compose.onNodeWithText("Fritanga Lola").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_stuck_empty)).assertDoesNotExist()
    }

    @Test
    fun anOrderShowsItsCustomerItsStatusItsTotalAndWhenItWasPlaced() {
        show(everything)

        compose.onNode(row(waiting) and hasAnyDescendant(hasText(text(R.string.admin_order_customer, "Ana Lopez")))).assertIsDisplayed()
        compose.onNode(row(waiting) and hasAnyDescendant(hasText(text(R.string.order_status_ready)))).assertIsDisplayed()
        compose.onNode(row(waiting) and hasAnyDescendant(hasText(text(R.string.price_nio, "295.00")))).assertIsDisplayed()
        compose.onNode(row(waiting) and hasAnyDescendant(hasText(text(R.string.order_placed_at, "14:25")))).assertIsDisplayed()
    }

    @Test
    fun theWaitingTimeShowsInMinutesAndIsAbsentWhileTheTimestampIsPending() {
        show(everything.copy(inKitchen = listOf(inKitchen.copy(createdAtMillis = 0L))))

        compose.onNode(row(waiting) and hasAnyDescendant(hasText(text(R.string.admin_order_waiting, 35)))).assertIsDisplayed()
        compose.onNode(row(withCourier) and hasAnyDescendant(hasText(text(R.string.admin_order_waiting, 20)))).assertIsDisplayed()
        compose.onNode(row(inKitchen) and hasAnyDescendant(hasText(text(R.string.admin_order_waiting, 0)))).assertDoesNotExist()
    }

    @Test
    fun aReadyOrderAndAKitchenOrderCanBeCancelledAndAClaimedOneCanBeReleased() {
        val events = show(everything)

        compose.onNodeWithTag(StuckOrdersTags.cancel("w1")).performClick()
        compose.onNodeWithTag(StuckOrdersTags.cancel("k1")).performClick()
        compose.onNodeWithTag(StuckOrdersTags.release("c1")).performClick()

        assertThat(events.cancelStarted).containsExactly("w1", "k1").inOrder()
        assertThat(events.released).containsExactly("c1")
    }

    @Test
    fun aClaimedOrderMustBeReleasedFirstSoItOffersNoCancelAndTheOthersNoRelease() {
        show(everything)

        compose.onNodeWithTag(StuckOrdersTags.cancel("c1")).assertDoesNotExist()
        compose.onNodeWithTag(StuckOrdersTags.release("w1")).assertDoesNotExist()
        compose.onNodeWithTag(StuckOrdersTags.release("k1")).assertDoesNotExist()
    }

    @Test
    fun theActionButtonsSayWhatTheyDo() {
        show(everything)

        compose.onNodeWithTag(StuckOrdersTags.cancel("w1")).assertTextEquals(text(R.string.admin_cancel_order))
        compose.onNodeWithTag(StuckOrdersTags.release("c1")).assertTextEquals(text(R.string.admin_release_claim))
    }

    @Test
    fun whileAnActionRunsEveryButtonIsDisabled() {
        show(everything.copy(busyOrderId = "w1"))

        compose.onNodeWithTag(StuckOrdersTags.cancel("w1")).assertIsNotEnabled()
        compose.onNodeWithTag(StuckOrdersTags.cancel("k1")).assertIsNotEnabled()
        compose.onNodeWithTag(StuckOrdersTags.release("c1")).assertIsNotEnabled()
    }

    @Test
    fun theButtonsAreEnabledWhenNothingRuns() {
        show(everything)

        compose.onNodeWithTag(StuckOrdersTags.cancel("w1")).assertIsEnabled()
        compose.onNodeWithTag(StuckOrdersTags.release("c1")).assertIsEnabled()
    }

    @Test
    fun anErrorIsShownAndCanBeDismissed() {
        val events = show(everything.copy(error = StuckOrdersError.ACTION_FAILED))

        compose.onNodeWithText(text(R.string.admin_stuck_action_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_dismiss)).performClick()

        assertThat(events.dismissedError).isEqualTo(1)
    }

    @Test
    fun noErrorIsShownWhenThereIsNone() {
        show(everything)

        compose.onNodeWithText(text(R.string.admin_stuck_action_failed)).assertDoesNotExist()
    }

    @Test
    fun theReasonDialogOpensForTheTargetOrderWithItsStateAndReportsWhatItIsAskedFor() {
        val events = show(everything.copy(cancelTarget = waiting, reasonInvalid = true, busyOrderId = "w1"))

        compose.onNodeWithText("dialog-for-w1-invalid=true-busy=true").assertIsDisplayed()
        compose.onNodeWithText("dialog-confirm").performClick()
        compose.onNodeWithText("dialog-dismiss").performClick()

        assertThat(events.confirmed).containsExactly("typed reason")
        assertThat(events.dismissedCancel).isEqualTo(1)
    }

    @Test
    fun noDialogIsComposedWithoutATarget() {
        show(everything)

        compose.onNodeWithText("dialog-confirm").assertDoesNotExist()
    }
}
