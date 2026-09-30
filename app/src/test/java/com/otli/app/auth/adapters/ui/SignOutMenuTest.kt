package com.otli.app.auth.adapters.ui

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
class SignOutMenuTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun openMenu() = compose.onNodeWithContentDescription(text(R.string.action_more_options)).performClick()

    @Test
    fun signOutIsHiddenUntilTheOverflowMenuIsOpened() {
        compose.setContent { SignOutMenuContent(onSignOut = {}) }

        compose.onNodeWithContentDescription(text(R.string.action_more_options)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_logout)).assertDoesNotExist()
    }

    @Test
    fun openingTheMenuOffersSignOutWithoutSigningOut() {
        var signOuts = 0
        compose.setContent { SignOutMenuContent(onSignOut = { signOuts++ }) }

        openMenu()

        compose.onNodeWithText(text(R.string.action_logout)).assertIsDisplayed()
        assertThat(signOuts).isEqualTo(0)
    }

    @Test
    fun choosingSignOutReportsItOnceAndClosesTheMenu() {
        var signOuts = 0
        compose.setContent { SignOutMenuContent(onSignOut = { signOuts++ }) }
        openMenu()

        compose.onNodeWithText(text(R.string.action_logout)).performClick()
        compose.waitForIdle()

        assertThat(signOuts).isEqualTo(1)
        compose.onNodeWithText(text(R.string.action_logout)).assertDoesNotExist()
    }
}
