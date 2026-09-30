package com.otli.app.core.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Customer home is the merchant list; a tap opens that merchant's storefront and back returns. */
@RunWith(RobolectricTestRunner::class)
class CustomerGraphTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val session = MutableStateFlow<SessionState>(SessionState.Active(Role.CUSTOMER))

    private fun launch() {
        compose.setContent {
            RootNavHost(
                session = session,
                customerHome = { open ->
                    Column {
                        Text("Marta", Modifier.testTag("list-m1").clickable { open("m1") })
                        Text("Sol", Modifier.testTag("list-m2").clickable { open("m2") })
                    }
                },
                storefront = { merchantId, onBack ->
                    Text("storefront-$merchantId", Modifier.testTag("storefront").clickable(onClick = onBack))
                },
            )
        }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun aCustomerLandsOnTheMerchantListNotOnAStorefront() {
        launch()

        compose.onNodeWithTag("list-m1").assertIsDisplayed()
        compose.onNodeWithTag("storefront").assertDoesNotExist()
    }

    @Test
    fun tappingAMerchantOpensThatMerchantsStorefront() {
        launch()

        compose.onNodeWithTag("list-m2").performClick()

        compose.onNodeWithText("storefront-m2").assertIsDisplayed()
        compose.onNodeWithTag("list-m2").assertDoesNotExist()
    }

    @Test
    fun anotherMerchantOpensItsOwnStorefront() {
        launch()

        compose.onNodeWithTag("list-m1").performClick()

        compose.onNodeWithText("storefront-m1").assertIsDisplayed()
    }

    @Test
    fun backFromTheStorefrontReturnsToTheList() {
        launch()
        compose.onNodeWithTag("list-m1").performClick()
        compose.onNodeWithText("storefront-m1").assertIsDisplayed()

        back()

        compose.onNodeWithTag("list-m1").assertIsDisplayed()
        compose.onNodeWithTag("storefront").assertDoesNotExist()
    }

    @Test
    fun theUpActionGivenToTheStorefrontReturnsToTheList() {
        launch()
        compose.onNodeWithTag("list-m2").performClick()
        compose.onNodeWithText("storefront-m2").assertIsDisplayed()

        compose.onNodeWithTag("storefront").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("list-m2").assertIsDisplayed()
        compose.onNodeWithTag("storefront").assertDoesNotExist()
    }

    @Test
    fun upFromAStorefrontThenOpeningAnotherMerchantWorksAgain() {
        launch()
        compose.onNodeWithTag("list-m1").performClick()
        compose.onNodeWithTag("storefront").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("list-m2").performClick()

        compose.onNodeWithText("storefront-m2").assertIsDisplayed()
    }

    @Test
    fun signingOutFromAStorefrontLeavesTheCustomerGraphEntirely() {
        launch()
        compose.onNodeWithTag("list-m1").performClick()
        compose.onNodeWithText("storefront-m1").assertIsDisplayed()

        session.value = SessionState.SignedOut
        compose.waitForIdle()

        compose.onNodeWithTag(RootTags.SIGNED_OUT).assertIsDisplayed()
        compose.onNodeWithTag("storefront").assertDoesNotExist()
    }
}
