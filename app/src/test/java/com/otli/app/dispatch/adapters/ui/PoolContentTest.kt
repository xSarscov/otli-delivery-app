package com.otli.app.dispatch.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.dispatch.application.aPoolOrder
import com.otli.app.dispatch.domain.PoolGate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class PoolContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private class Events {
        val claimed = mutableListOf<String>()
        var dismissed = 0
    }

    private fun show(state: PoolUiState): Events {
        val events = Events()
        compose.setContent {
            PoolContent(state = state, onClaim = { events.claimed += it }, onDismissMessage = { events.dismissed++ })
        }
        return events
    }

    private fun open(vararg orders: com.otli.app.dispatch.domain.PoolOrder) =
        PoolUiState(isLoading = false, gate = PoolGate.OPEN, orders = orders.toList())

    @Test
    fun itShowsProgressWhileLoading() {
        show(PoolUiState())

        compose.onNodeWithTag(PoolTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun anOfflineCourierIsToldToGoOnlineAndSeesNoOrders() {
        show(PoolUiState(isLoading = false, gate = PoolGate.OFFLINE, orders = listOf(aPoolOrder("o1"))))

        compose.onNodeWithText(text(R.string.pool_offline)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.pool_title)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.pool_empty)).assertDoesNotExist()
        compose.onNodeWithText("Comedor Marta").assertDoesNotExist()
        compose.onNodeWithTag(PoolTags.LOADING).assertDoesNotExist()
    }

    @Test
    fun aBusyCourierSeesNothingAtAll() {
        show(PoolUiState(isLoading = false, gate = PoolGate.BUSY, orders = listOf(aPoolOrder("o1"))))

        compose.onNodeWithText(text(R.string.pool_offline)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.pool_empty)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.pool_title)).assertDoesNotExist()
        compose.onNodeWithText("Comedor Marta").assertDoesNotExist()
    }

    @Test
    fun anOpenPoolWithoutOrdersSaysNothingIsWaiting() {
        show(open())

        compose.onNodeWithText(text(R.string.pool_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.pool_empty)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.pool_offline)).assertDoesNotExist()
    }

    @Test
    fun anOrderShowsWhereToPickItUpWhereToTakeItAndWhatTheCourierEarns() {
        show(open(aPoolOrder("o1", merchantName = "Comedor Marta")))

        compose.onNodeWithText("Comedor Marta").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.pool_pickup, "Frente al parque")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.pool_dropoff, "Casa azul")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.pool_fee, text(R.string.price_nio, "30.00"))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.pool_cash, text(R.string.price_nio, "270.00"))).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.pool_empty)).assertDoesNotExist()
    }

    @Test
    fun everyOrderHasItsOwnClaimButtonThatReportsTheOrder() {
        val events = show(open(aPoolOrder("o1", merchantName = "Comedor Marta"), aPoolOrder("o2", merchantName = "Pulperia Sol")))

        assertThat(compose.onAllNodesWithText(text(R.string.pool_claim)).fetchSemanticsNodes()).hasSize(2)
        compose.onNodeWithTag(PoolTags.claim("o2")).performClick()

        assertThat(events.claimed).containsExactly("o2")
    }

    @Test
    fun anOrderBeingClaimedCannotBeTappedAgain() {
        show(open(aPoolOrder("o1"), aPoolOrder("o2")).copy(claiming = setOf("o1")))

        compose.onNodeWithTag(PoolTags.claim("o1")).assertIsNotEnabled()
        compose.onNodeWithTag(PoolTags.claim("o2")).assertIsDisplayed()
    }

    @Test
    fun theTakenMessageIsShownAndCanBeDismissed() {
        val events = show(open(aPoolOrder("o1")).copy(message = PoolMessage.ALREADY_TAKEN))

        compose.onNodeWithText(text(R.string.pool_message_taken)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_dismiss)).performClick()

        assertThat(events.dismissed).isEqualTo(1)
    }

    @Test
    fun everyMessageHasItsOwnWording() {
        val wording = mapOf(
            PoolMessage.ALREADY_TAKEN to R.string.pool_message_taken,
            PoolMessage.NOT_READY to R.string.pool_message_not_ready,
            PoolMessage.BUSY to R.string.pool_message_busy,
            PoolMessage.OFFLINE to R.string.pool_message_offline,
            PoolMessage.NOT_ACTIVE to R.string.pool_message_not_active,
            PoolMessage.CLAIM_FAILED to R.string.pool_message_failed,
            PoolMessage.LOAD_FAILED to R.string.pool_message_load,
        )

        assertThat(wording.keys).containsExactlyElementsIn(PoolMessage.entries)
        assertThat(wording.values.toSet()).hasSize(wording.size)
        wording.forEach { (message, string) -> assertThat(PoolMessages.of(message)).isEqualTo(string) }
    }
}
