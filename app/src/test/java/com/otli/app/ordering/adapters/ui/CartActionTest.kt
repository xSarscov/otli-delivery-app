package com.otli.app.ordering.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CartActionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    @Test
    fun anEmptyCartShowsThePlainButtonWithoutABadge() {
        compose.setContent { CartAction(itemCount = 0, onClick = {}) }

        compose.onNodeWithContentDescription(text(R.string.cart_open)).assertIsDisplayed()
        compose.onNodeWithText("0").assertDoesNotExist()
    }

    @Test
    fun aCartWithItemsShowsTheCountAsABadgeAndInTheDescription() {
        compose.setContent { CartAction(itemCount = 3, onClick = {}) }

        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.cart_open_with_count, 3)).assertIsDisplayed()
    }

    @Test
    fun aSingleItemAlreadyShowsItsBadge() {
        compose.setContent { CartAction(itemCount = 1, onClick = {}) }

        compose.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun aDifferentCountShowsThatCount() {
        compose.setContent { CartAction(itemCount = 12, onClick = {}) }

        compose.onNodeWithText("12").assertIsDisplayed()
    }

    @Test
    fun tappingOpensTheCart() {
        var opened = 0
        compose.setContent { CartAction(itemCount = 1, onClick = { opened++ }) }

        compose.onNodeWithContentDescription(text(R.string.cart_open_with_count, 1)).performClick()

        assertThat(opened).isEqualTo(1)
    }
}
