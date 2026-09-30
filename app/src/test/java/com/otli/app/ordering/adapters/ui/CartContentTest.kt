package com.otli.app.ordering.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.catalog.domain.Product
import com.otli.app.core.money.Money
import com.otli.app.ordering.application.PendingReplacement
import com.otli.app.ordering.domain.CartMerchant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class CartContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private val filled = CartUiState(
        merchantName = "Comedor Marta",
        lines = listOf(
            CartLineUi("p1", "Nacatamal", Money(12050), 2, Money(24100)),
            CartLineUi("p2", "Fresco", Money(2500), 1, Money(2500)),
        ),
        subtotal = Money(26600),
        itemCount = 3,
    )

    private class Taps {
        val increased = mutableListOf<String>()
        val decreased = mutableListOf<String>()
        val removed = mutableListOf<String>()
        var checkouts = 0
        var backs = 0
    }

    private fun show(state: CartUiState, taps: Taps = Taps()): Taps {
        compose.setContent {
            CartContent(
                state = state,
                onIncrease = { taps.increased += it },
                onDecrease = { taps.decreased += it },
                onRemove = { taps.removed += it },
                onCheckout = { taps.checkouts++ },
                onBack = { taps.backs++ },
            )
        }
        return taps
    }

    @Test
    fun showsTheStoreEachLineWithQuantityAndTotalAndTheSubtotal() {
        show(filled)

        compose.onNodeWithText(text(R.string.cart_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.cart_from_store, "Comedor Marta")).assertIsDisplayed()
        compose.onNodeWithText("Nacatamal").assertIsDisplayed()
        compose.onNodeWithText("Fresco").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.price_nio, "241.00")).assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.cart_subtotal)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.price_nio, "266.00")).assertIsDisplayed()
    }

    @Test
    fun aLinesButtonsReportThatProductOnly() {
        val taps = show(filled)

        compose.onNodeWithContentDescription(text(R.string.cart_increase, "Nacatamal")).performClick()
        compose.onNodeWithContentDescription(text(R.string.cart_decrease, "Fresco")).performClick()
        compose.onNodeWithContentDescription(text(R.string.cart_remove, "Nacatamal")).performClick()

        assertThat(taps.increased).containsExactly("p1")
        assertThat(taps.decreased).containsExactly("p2")
        assertThat(taps.removed).containsExactly("p1")
    }

    @Test
    fun checkoutAndBackAreReported() {
        val taps = show(filled)

        compose.onNodeWithText(text(R.string.cart_checkout)).performClick()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        assertThat(taps.checkouts).isEqualTo(1)
        assertThat(taps.backs).isEqualTo(1)
    }

    @Test
    fun anEmptyCartSaysSoAndOffersNoCheckout() {
        show(CartUiState())

        compose.onNodeWithText(text(R.string.cart_empty)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.cart_checkout)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.cart_subtotal)).assertDoesNotExist()
    }

    @Test
    fun theReplaceDialogNamesTheCurrentStoreTheProductAndTheNewStore() {
        val pending = PendingReplacement(
            current = CartMerchant("m1", "Comedor Marta"),
            merchant = CartMerchant("m2", "Pulperia Sol"),
            product = Product("p9", "c1", "Vigoron", "", Money(8000), isAvailable = true, photoVersion = 0),
        )
        compose.setContent { ReplaceCartDialog(pending = pending, onConfirm = {}, onDismiss = {}) }

        compose.onNodeWithText(text(R.string.replace_cart_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.replace_cart_message, "Comedor Marta", "Vigoron", "Pulperia Sol")).assertIsDisplayed()
    }

    @Test
    fun theReplaceDialogReportsConfirmAndDismissSeparately() {
        val pending = PendingReplacement(
            CartMerchant("m1", "Comedor Marta"),
            CartMerchant("m2", "Pulperia Sol"),
            Product("p9", "c1", "Vigoron", "", Money(8000), isAvailable = true, photoVersion = 0),
        )
        var confirmed = 0
        var dismissed = 0
        compose.setContent { ReplaceCartDialog(pending, onConfirm = { confirmed++ }, onDismiss = { dismissed++ }) }

        compose.onNodeWithText(text(R.string.replace_cart_confirm)).performClick()
        assertThat(confirmed).isEqualTo(1)
        assertThat(dismissed).isEqualTo(0)

        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        assertThat(dismissed).isEqualTo(1)
        assertThat(confirmed).isEqualTo(1)
    }
}
