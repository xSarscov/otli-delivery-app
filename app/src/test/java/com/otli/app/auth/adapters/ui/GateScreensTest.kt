package com.otli.app.auth.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.auth.domain.Role
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GateScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun pendingMerchantSeesTheStoreApprovalMessageOnly() {
        compose.setContent { PendingApprovalContent(role = Role.MERCHANT, onLogout = {}) }

        compose.onNodeWithText(text(R.string.gate_pending_merchant)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.gate_pending_courier)).assertDoesNotExist()
    }

    @Test
    fun pendingCourierSeesTheCourierApprovalMessageOnly() {
        compose.setContent { PendingApprovalContent(role = Role.COURIER, onLogout = {}) }

        compose.onNodeWithText(text(R.string.gate_pending_courier)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.gate_pending_merchant)).assertDoesNotExist()
    }

    @Test
    fun suspendedMessageIsRoleAppropriateForEveryRole() {
        val expected = mapOf(
            Role.CUSTOMER to R.string.gate_suspended_customer,
            Role.MERCHANT to R.string.gate_suspended_merchant,
            Role.COURIER to R.string.gate_suspended_courier,
        )
        var role by mutableStateOf(Role.CUSTOMER)
        compose.setContent { SuspendedContent(role = role, onLogout = {}) }

        for ((expectedRole, message) in expected) {
            role = expectedRole
            compose.waitForIdle()

            compose.onNodeWithText(text(message)).assertIsDisplayed()
            for (other in expected.values - message) {
                compose.onNodeWithText(text(other)).assertDoesNotExist()
            }
        }
    }

    @Test
    fun pendingAndSuspendedMessagesAreDifferent() {
        assertThat(text(R.string.gate_pending_merchant)).isNotEqualTo(text(R.string.gate_suspended_merchant))
        assertThat(text(R.string.gate_pending_courier)).isNotEqualTo(text(R.string.gate_suspended_courier))
    }

    @Test
    fun logoutButtonInvokesTheCallbackOnBothGates() {
        var logouts = 0
        compose.setContent { PendingApprovalContent(role = Role.MERCHANT, onLogout = { logouts++ }) }
        compose.onNodeWithText(text(R.string.action_logout)).performClick()
        assertThat(logouts).isEqualTo(1)
    }

    @Test
    fun suspendedGateAlsoOffersLogout() {
        var logouts = 0
        compose.setContent { SuspendedContent(role = Role.COURIER, onLogout = { logouts++ }) }
        compose.onNodeWithText(text(R.string.action_logout)).performClick()
        assertThat(logouts).isEqualTo(1)
    }

    @Test
    fun signOutIsAnEnabledClickableActionOnThePendingGate() {
        compose.setContent { PendingApprovalContent(role = Role.MERCHANT, onLogout = {}) }
        compose.onNodeWithText(text(R.string.action_logout)).assertIsEnabled().assertHasClickAction()
    }

    @Test
    fun signOutIsAnEnabledClickableActionOnTheSuspendedGate() {
        compose.setContent { SuspendedContent(role = Role.COURIER, onLogout = {}) }
        compose.onNodeWithText(text(R.string.action_logout)).assertIsEnabled().assertHasClickAction()
    }
}
