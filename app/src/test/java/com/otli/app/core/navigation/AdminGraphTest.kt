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

/** Admin home opens one order's detail; back from the detail returns to the home. */
@RunWith(RobolectricTestRunner::class)
class AdminGraphTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val session = MutableStateFlow<SessionState>(SessionState.Active(Role.ADMIN))

    private fun launch() {
        compose.setContent {
            RootNavHost(
                session = session,
                adminHome = { openOrder ->
                    Column {
                        Text("admin-home", Modifier.testTag("admin-home"))
                        Text("order-row-7", Modifier.testTag("order-7").clickable { openOrder("7") })
                        Text("order-row-9", Modifier.testTag("order-9").clickable { openOrder("9") })
                    }
                },
                adminOrder = { orderId, onBack -> Text("detail-$orderId", Modifier.testTag("detail").clickable(onClick = onBack)) },
            )
        }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun anAdminLandsOnTheAdminHomeNotOnAnOrder() {
        launch()

        compose.onNodeWithTag("admin-home").assertIsDisplayed()
        compose.onNodeWithTag("detail").assertDoesNotExist()
    }

    @Test
    fun tappingAnOrderOpensThatOrdersDetail() {
        launch()

        compose.onNodeWithTag("order-9").performClick()

        compose.onNodeWithText("detail-9").assertIsDisplayed()
        compose.onNodeWithTag("admin-home").assertDoesNotExist()
    }

    @Test
    fun anotherOrderOpensItsOwnDetail() {
        launch()

        compose.onNodeWithTag("order-7").performClick()

        compose.onNodeWithText("detail-7").assertIsDisplayed()
    }

    @Test
    fun backFromTheDetailReturnsToTheHome() {
        launch()
        compose.onNodeWithTag("order-7").performClick()
        compose.onNodeWithText("detail-7").assertIsDisplayed()

        back()

        compose.onNodeWithTag("admin-home").assertIsDisplayed()
        compose.onNodeWithTag("detail").assertDoesNotExist()
    }

    @Test
    fun theUpActionGivenToTheDetailReturnsToTheHome() {
        launch()
        compose.onNodeWithTag("order-9").performClick()

        compose.onNodeWithTag("detail").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("admin-home").assertIsDisplayed()
    }

    @Test
    fun signingOutLeavesTheAdminGraph() {
        launch()
        compose.onNodeWithTag("order-9").performClick()

        session.value = SessionState.SignedOut
        compose.waitForIdle()

        compose.onNodeWithTag("detail").assertDoesNotExist()
        compose.onNodeWithTag("admin-home").assertDoesNotExist()
        compose.onNodeWithTag(RootTags.SIGNED_OUT).assertIsDisplayed()
    }
}
