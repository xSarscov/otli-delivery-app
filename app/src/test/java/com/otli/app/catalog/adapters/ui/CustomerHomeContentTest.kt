package com.otli.app.catalog.adapters.ui

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
import org.robolectric.annotation.Config

/** The customer home is the merchant list under an app bar that carries the sign-out entry. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1200dp")
class CustomerHomeContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private val loaded = MerchantListUiState(
        isLoading = false,
        merchants = listOf(aMerchant("m1", "Comedor Marta", isOpen = true)),
    )

    private fun show(
        state: MerchantListUiState = loaded,
        onOpen: (String) -> Unit = {},
        onSignOut: () -> Unit = {},
    ) {
        compose.setContent { CustomerHomeContent(state, onMerchantClick = onOpen, onSignOut = onSignOut) }
    }

    @Test
    fun showsTheAppBarAboveTheMerchantList() {
        show()

        compose.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithText("Comedor Marta").assertIsDisplayed()
    }

    @Test
    fun signingOutFromTheOverflowMenuIsReported() {
        var signOuts = 0
        show(onSignOut = { signOuts++ })

        compose.onNodeWithContentDescription(text(R.string.action_more_options)).performClick()
        compose.onNodeWithText(text(R.string.action_logout)).performClick()

        assertThat(signOuts).isEqualTo(1)
    }

    @Test
    fun signOutStaysReachableWhileTheListLoads() {
        var signOuts = 0
        show(MerchantListUiState(isLoading = true), onSignOut = { signOuts++ })

        compose.onNodeWithContentDescription(text(R.string.action_more_options)).performClick()
        compose.onNodeWithText(text(R.string.action_logout)).performClick()

        assertThat(signOuts).isEqualTo(1)
    }

    @Test
    fun tappingAMerchantStillOpensItsStorefront() {
        val opened = mutableListOf<String>()
        show(onOpen = { opened += it })

        compose.onNodeWithText("Comedor Marta").performClick()

        assertThat(opened).containsExactly("m1")
    }
}
