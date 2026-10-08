package com.otli.app.admin.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.ordering.adapters.ui.OrderTrackingUiState
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class OrderDetailContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private fun show(state: OrderTrackingUiState, onBack: () -> Unit = {}) {
        compose.setContent {
            OrderDetailContent(state = state, onBack = onBack, liveMap = { Text("live-map-slot") })
        }
    }

    private fun loaded(order: com.otli.app.ordering.domain.Order) = OrderTrackingUiState(isLoading = false, order = order)

    @Test
    fun itHasATitleAndGoesBack() {
        var backs = 0
        show(OrderTrackingUiState(), onBack = { backs++ })

        compose.onNodeWithText(text(R.string.admin_order_detail_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun itShowsProgressWhileLoading() {
        show(OrderTrackingUiState())

        compose.onNodeWithTag(OrderDetailTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun anOrderThatIsNotThereAndAFailedLoadSaySo() {
        show(OrderTrackingUiState(isLoading = false, notFound = true))
        compose.onNodeWithText(text(R.string.tracking_not_found)).assertIsDisplayed()
    }

    @Test
    fun aFailedLoadSaysSo() {
        show(OrderTrackingUiState(isLoading = false, loadFailed = true))

        compose.onNodeWithText(text(R.string.tracking_load_failed)).assertIsDisplayed()
    }

    @Test
    fun itShowsTheStoreTheStatusAndWhoOrdered() {
        show(loaded(anOrder("o1", OrderStatus.PREPARING, merchantName = "Comedor Marta")))

        compose.onNodeWithText(text(R.string.cart_from_store, "Comedor Marta")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.order_status_preparing)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_order_customer, "Ana Lopez")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_order_phone, "+50588880201")).assertIsDisplayed()
    }

    @Test
    fun itShowsWhereToPickUpAndWhereToDeliver() {
        show(loaded(anOrder("o1", OrderStatus.READY)))

        compose.onNodeWithText(text(R.string.admin_order_pickup, "Frente al parque")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_order_dropoff, "Casa azul")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun itListsWhatWasOrderedAndTheTotals() {
        show(loaded(anOrder("o1", OrderStatus.PLACED)))

        compose.onNodeWithText("2 x Nacatamal, 1 x Fresco").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.price_nio, "30.00")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.price_nio, "295.00")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theCourierAccountShowsOnlyOnceOneIsAssigned() {
        show(loaded(anOrder("o1", OrderStatus.CLAIMED, courierId = "courier-9")))

        compose.onNodeWithText(text(R.string.admin_order_courier, "courier-9")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anOrderWithoutACourierShowsNoCourierLine() {
        show(loaded(anOrder("o1", OrderStatus.READY)))

        compose.onNodeWithText(text(R.string.admin_order_courier, "")).assertDoesNotExist()
    }

    @Test
    fun aCancelledOrderShowsWhyAndARejectedOneShowsTheStoresReason() {
        show(loaded(anOrder("o1", OrderStatus.CANCELLED, cancelReason = "Store closed early")))
        compose.onNodeWithText(text(R.string.tracking_cancel_reason, "Store closed early")).assertIsDisplayed()
    }

    @Test
    fun aRejectedOrderShowsTheStoresReason() {
        show(loaded(anOrder("o1", OrderStatus.REJECTED, rejectReason = "Out of gas")))

        compose.onNodeWithText(text(R.string.tracking_reject_reason, "Out of gas")).assertIsDisplayed()
    }

    @Test
    fun aReasonIsShownOnlyForTheStatusItBelongsTo() {
        show(loaded(anOrder("o1", OrderStatus.ACCEPTED, rejectReason = "stale reject", cancelReason = "stale cancel")))

        compose.onNodeWithText(text(R.string.tracking_reject_reason, "stale reject")).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tracking_cancel_reason, "stale cancel")).assertDoesNotExist()
    }

    @Test
    fun aBlankReasonIsNotShown() {
        show(loaded(anOrder("o1", OrderStatus.CANCELLED, cancelReason = " ")))

        compose.onNodeWithText(text(R.string.tracking_cancel_reason, " ")).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.order_status_cancelled)).assertIsDisplayed()
    }

    @Test
    fun aFailedLoadHidesAStaleOrder() {
        show(OrderTrackingUiState(isLoading = false, loadFailed = true, order = anOrder("o1", OrderStatus.READY, merchantName = "Comedor Marta")))

        compose.onNodeWithText(text(R.string.tracking_load_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.cart_from_store, "Comedor Marta")).assertDoesNotExist()
    }

    @Test
    fun theLiveMapSlotIsHostedOnlyWhenTheOrderIsLoaded() {
        show(loaded(anOrder("o1", OrderStatus.PICKED_UP, courierId = "courier-1")))
        compose.onNodeWithText("live-map-slot").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noLiveMapSlotWhileLoading() {
        show(OrderTrackingUiState())

        compose.onNodeWithText("live-map-slot").assertDoesNotExist()
    }
}
