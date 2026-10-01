package com.otli.app.ordering.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
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
class MerchantOrderBoardContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private class Events {
        val advanced = mutableListOf<String>()
        var dismissedError = 0
    }

    private fun show(state: MerchantOrderBoardUiState, events: Events = Events()): Events {
        compose.setContent {
            MerchantOrderBoardContent(
                state = state,
                onAdvance = { events.advanced += it },
                onDismissError = { events.dismissedError++ },
            )
        }
        return events
    }

    private fun loaded(
        incoming: List<com.otli.app.ordering.domain.Order> = emptyList(),
        inProgress: List<com.otli.app.ordering.domain.Order> = emptyList(),
    ) = MerchantOrderBoardUiState(isLoading = false, incoming = incoming, inProgress = inProgress)

    @Test
    fun itShowsProgressWhileLoading() {
        show(MerchantOrderBoardUiState())

        compose.onNodeWithTag(OrderBoardTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun anEmptyBoardSaysNothingIsWaiting() {
        show(loaded())

        compose.onNodeWithText(text(R.string.board_empty)).assertIsDisplayed()
        compose.onNodeWithTag(OrderBoardTags.LOADING).assertDoesNotExist()
    }

    @Test
    fun aNewOrderShowsWhoOrderedWhatAndForHowMuch() {
        show(loaded(incoming = listOf(anOrder("o1", OrderStatus.PLACED))))

        compose.onNodeWithText(text(R.string.board_incoming_title)).assertIsDisplayed()
        compose.onNodeWithText("Ana Lopez").assertIsDisplayed()
        compose.onNodeWithText("2 x Nacatamal, 1 x Fresco").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.board_order_total, text(R.string.price_nio, "295.00"))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.board_empty)).assertDoesNotExist()
    }

    @Test
    fun acceptReportsTheOrder() {
        val events = show(loaded(incoming = listOf(anOrder("o1", OrderStatus.PLACED))))

        compose.onNodeWithText(text(R.string.board_accept)).performClick()
        assertThat(events.advanced).containsExactly("o1")
    }

    @Test
    fun anOrderWithAStepInFlightCannotBeTappedAgain() {
        show(loaded(incoming = listOf(anOrder("o1", OrderStatus.PLACED))).copy(busy = setOf("o1")))

        compose.onNodeWithText(text(R.string.board_accept)).assertIsNotEnabled()
    }

    @Test
    fun anAcceptedOrderOffersToStartPreparing() {
        val events = show(loaded(inProgress = listOf(anOrder("o1", OrderStatus.ACCEPTED))))

        compose.onNodeWithText(text(R.string.board_in_progress_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.board_empty)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.board_start_preparing)).performClick()

        assertThat(events.advanced).containsExactly("o1")
    }

    @Test
    fun anInProgressOrderWithAStepInFlightCannotBeTappedAgain() {
        show(loaded(inProgress = listOf(anOrder("o1", OrderStatus.ACCEPTED))).copy(busy = setOf("o1")))

        compose.onNodeWithText(text(R.string.board_start_preparing)).assertIsNotEnabled()
    }

    @Test
    fun aPreparingOrderOffersToMarkItReady() {
        val events = show(loaded(inProgress = listOf(anOrder("o1", OrderStatus.PREPARING))))

        compose.onNodeWithText(text(R.string.board_mark_ready)).performClick()

        assertThat(events.advanced).containsExactly("o1")
    }

    @Test
    fun aReadyOrderOnlyShowsItsStatusBecauseTheMerchantHasNoStepLeft() {
        show(loaded(inProgress = listOf(anOrder("o1", OrderStatus.READY), anOrder("o2", OrderStatus.CLAIMED))))

        compose.onNodeWithText(text(R.string.order_status_ready)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.order_status_claimed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.board_mark_ready)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.board_start_preparing)).assertDoesNotExist()
    }

    @Test
    fun errorsAreShownAndCanBeDismissed() {
        val events = show(loaded().copy(error = OrderBoardError.ACTION_FAILED))

        compose.onNode(hasText(text(R.string.board_error_action))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_dismiss)).performClick()

        assertThat(events.dismissedError).isEqualTo(1)
    }

    @Test
    fun aLoadErrorHasItsOwnMessage() {
        show(loaded().copy(error = OrderBoardError.LOAD_FAILED))

        compose.onNodeWithText(text(R.string.board_error_load)).assertIsDisplayed()
    }
}
