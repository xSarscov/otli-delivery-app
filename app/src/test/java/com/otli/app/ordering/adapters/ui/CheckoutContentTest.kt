package com.otli.app.ordering.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.core.map.MapPin
import com.otli.app.core.money.Money
import com.otli.app.ordering.application.PlaceOrderRejection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The MapLibre view is replaced by a fake slot that reports one tap; native rendering is checked on a device. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class CheckoutContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private val ready = CheckoutUiState(
        merchantName = "Comedor Marta",
        subtotal = Money(26500),
        fee = Money(3000),
        isCartEmpty = false,
    )

    private class Events {
        val pins = mutableListOf<MapPin?>()
        val references = mutableListOf<String>()
        val cash = mutableListOf<Boolean>()
        var places = 0
        var backs = 0
    }

    private fun show(state: CheckoutUiState, events: Events = Events()): Events {
        compose.setContent {
            CheckoutContent(
                state = state,
                onPinChange = { events.pins += it },
                onReferenceChange = { events.references += it },
                onCashConfirmedChange = { events.cash += it },
                onPlace = { events.places++ },
                onBack = { events.backs++ },
                mapContent = { _, onTap -> Box(Modifier.testTag("fake-map").clickable { onTap(12.31, -86.61) }) { Text("map") } },
            )
        }
        return events
    }

    @Test
    fun showsTheStoreAndTheSubtotalFeeAndTotal() {
        show(ready)

        compose.onNodeWithText(text(R.string.checkout_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.cart_from_store, "Comedor Marta")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.price_nio, "265.00")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.price_nio, "30.00")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.price_nio, "295.00")).assertIsDisplayed()
    }

    @Test
    fun anUnknownFeeShowsNoTotalInsteadOfAWrongOne() {
        show(ready.copy(fee = null))

        compose.onNodeWithText(text(R.string.price_nio, "265.00")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.checkout_fee_unknown)).assertIsDisplayed()
    }

    @Test
    fun tappingTheMapReportsAPinAndTheFieldsReportTheirValues() {
        val events = show(ready)

        compose.onNodeWithTag("fake-map").performClick()
        compose.onNodeWithText(text(R.string.checkout_reference_label)).performScrollTo().performTextInput("Casa azul")
        compose.onNodeWithText(text(R.string.checkout_cash_label), substring = true).performScrollTo().performClick()

        assertThat(events.pins).containsExactly(MapPin(12.31, -86.61))
        assertThat(events.references).containsExactly("Casa azul")
        assertThat(events.cash).containsExactly(true)
    }

    @Test
    fun theCashCheckboxReflectsTheStateAndOffersToUntick() {
        val events = show(ready.copy(cashConfirmed = true))

        compose.onNodeWithText(text(R.string.checkout_cash_label), substring = true).performScrollTo().performClick()

        assertThat(events.cash).containsExactly(false)
    }

    @Test
    fun theCheckboxIsTickedOnlyOnceCashIsConfirmed() {
        show(ready.copy(cashConfirmed = true))
        compose.onNode(isToggleable()).performScrollTo().assertIsOn()
    }

    @Test
    fun theCheckboxStartsUnticked() {
        show(ready)
        compose.onNode(isToggleable()).performScrollTo().assertIsOff()
    }

    @Test
    fun tickingTheCheckboxItselfConfirmsCash() {
        val events = show(ready)
        compose.onNode(isToggleable()).performScrollTo().performClick()
        assertThat(events.cash).containsExactly(true)
    }

    @Test
    fun placeOrderIsReportedAndBackLeaves() {
        val events = show(ready)

        compose.onNodeWithText(text(R.string.checkout_place)).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        assertThat(events.places).isEqualTo(1)
        assertThat(events.backs).isEqualTo(1)
    }

    @Test
    fun placeOrderIsDisabledWhilePlacing() {
        show(ready.copy(isPlacing = true))
        compose.onNodeWithText(text(R.string.checkout_place)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun anEmptyCartCannotBePlaced() {
        show(ready.copy(isCartEmpty = true))

        compose.onNodeWithText(text(R.string.checkout_place)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aTypedReferenceAndPinAreShown() {
        show(ready.copy(reference = "Casa azul", pin = MapPin(12.2656, -86.5664)))

        compose.onNodeWithText("Casa azul").assertIsDisplayed()
        compose.onNodeWithText("12.26560, -86.56640", substring = true).assertIsDisplayed()
    }

    private fun assertShows(error: CheckoutError, expected: String) {
        show(ready.copy(error = error))
        compose.onNodeWithText(expected).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMissingPinTellsTheCustomerToDropOne() = assertShows(CheckoutError.PinRequired, text(R.string.checkout_error_pin))

    @Test
    fun aMissingReferenceAsksForOne() = assertShows(CheckoutError.ReferenceRequired, text(R.string.checkout_error_reference))

    @Test
    fun unconfirmedCashAsksForConfirmation() = assertShows(CheckoutError.CashNotConfirmed, text(R.string.checkout_error_cash))

    @Test
    fun aChangedFeeShowsTheNewFee() =
        assertShows(CheckoutError.FeeChanged(Money(4500)), text(R.string.checkout_error_fee_changed, "45.00"))

    @Test
    fun aClosedStoreIsNamed() =
        assertShows(CheckoutError.Rejected(PlaceOrderRejection.StoreClosed("Pulperia Sol")), text(R.string.checkout_error_store_closed, "Pulperia Sol"))

    @Test
    fun anUnavailableStoreIsNamed() = assertShows(
        CheckoutError.Rejected(PlaceOrderRejection.StoreUnavailable("Pulperia Sol")),
        text(R.string.checkout_error_store_unavailable, "Pulperia Sol"),
    )

    @Test
    fun unavailableItemsAreListedByName() = assertShows(
        CheckoutError.Rejected(PlaceOrderRejection.ItemsUnavailable(listOf("Fresco", "Vigoron"))),
        text(R.string.checkout_error_items_unavailable, "Fresco, Vigoron"),
    )

    @Test
    fun tooManyItemsStatesTheLimit() =
        assertShows(CheckoutError.Rejected(PlaceOrderRejection.TooManyItems(30)), text(R.string.checkout_error_too_many_items, 30))

    @Test
    fun anUnreadableProfileIsExplained() {
        assertShows(CheckoutError.ProfileUnavailable, text(R.string.checkout_error_profile))
    }

    @Test
    fun aFailedPlacementIsExplained() {
        assertShows(CheckoutError.Rejected(PlaceOrderRejection.PlacementFailed), text(R.string.checkout_error_failed))
    }

    @Test
    fun anUnreadableFeeIsExplained() =
        assertShows(CheckoutError.Rejected(PlaceOrderRejection.FeeUnavailable), text(R.string.checkout_error_fee_unavailable))

    @Test
    fun anEmptyCartRejectionIsExplained() =
        assertShows(CheckoutError.Rejected(PlaceOrderRejection.EmptyCart), text(R.string.checkout_error_empty))

    @Test
    fun noErrorMeansNoErrorText() {
        show(ready)

        compose.onNodeWithText(text(R.string.checkout_error_pin)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.checkout_error_failed)).assertDoesNotExist()
    }
}
