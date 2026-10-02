package com.otli.app.dispatch.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CourierDashboardContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun show(onSignOut: () -> Unit = {}) {
        compose.setContent {
            CourierDashboardContent(
                onSignOut = onSignOut,
                availability = { Text("availability slot", Modifier.testTag("availability-slot")) },
                tracking = { Text("tracking slot", Modifier.testTag("tracking-slot")) },
                pool = { Text("pool slot", Modifier.testTag("pool-slot")) },
                activeDelivery = { Text("delivery slot", Modifier.testTag("delivery-slot")) },
            )
        }
    }

    @Test
    fun itHasTheAppBarAndHostsTheAvailabilitySwitchTheTrackingStatusThePoolAndTheActiveDelivery() {
        show()

        compose.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithTag("availability-slot").assertIsDisplayed()
        compose.onNodeWithTag("tracking-slot").assertIsDisplayed()
        compose.onNodeWithTag("pool-slot").assertIsDisplayed()
        compose.onNodeWithTag("delivery-slot").assertIsDisplayed()
    }

    @Test
    fun signingOutIsInTheOverflowMenuAndReportsTheRequest() {
        var signedOut = 0
        show(onSignOut = { signedOut++ })
        compose.onNodeWithText(text(R.string.action_logout)).assertDoesNotExist()

        compose.onNodeWithContentDescription(text(R.string.action_more_options)).performClick()
        compose.onNodeWithText(text(R.string.action_logout)).performClick()

        assertThat(signedOut).isEqualTo(1)
    }
}
