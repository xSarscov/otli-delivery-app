package com.otli.app.admin.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.admin.application.anAccount
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class ApprovalsContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private class Events {
        val approved = mutableListOf<String>()
        val suspended = mutableListOf<String>()
        var dismissed = 0
    }

    private fun show(state: ApprovalsUiState, events: Events = Events()): Events {
        compose.setContent {
            ApprovalsContent(
                state = state,
                onApprove = { events.approved += it.uid },
                onSuspend = { events.suspended += it.uid },
                onDismissError = { events.dismissed++ },
            )
        }
        return events
    }

    private val pendingMerchant = anAccount("m-new", Role.MERCHANT, AccountStatus.PENDING, "Nuevo Comercio")
    private val pendingCourier = anAccount("c-new", Role.COURIER, AccountStatus.PENDING, "Nuevo Repartidor")
    private val activeMerchant = anAccount("m1", Role.MERCHANT, AccountStatus.ACTIVE, "Comedor Marta")
    private val suspendedCourier = anAccount("c1", Role.COURIER, AccountStatus.SUSPENDED, "Luis Mendoza")

    private val everything = ApprovalsUiState(
        isLoading = false,
        pending = listOf(pendingMerchant, pendingCourier),
        active = listOf(activeMerchant),
        suspended = listOf(suspendedCourier),
    )

    @Test
    fun itShowsProgressWhileLoading() {
        show(ApprovalsUiState())

        compose.onNodeWithTag(ApprovalsTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun aFailedLoadSaysSo() {
        show(ApprovalsUiState(isLoading = false, loadFailed = true))

        compose.onNodeWithText(text(R.string.admin_approvals_load_failed)).assertIsDisplayed()
    }

    @Test
    fun everyAccountShowsItsNameItsRoleAndHowToReachIt() {
        show(everything)

        compose.onNodeWithText("Nuevo Comercio").assertIsDisplayed()
        compose.onNodeWithText("Nuevo Repartidor").assertIsDisplayed()
        compose.onNodeWithText("Comedor Marta").assertIsDisplayed()
        compose.onNodeWithText("Luis Mendoza").assertIsDisplayed()
        compose.onNode(hasTestTag(ApprovalsTags.row("m-new")) and hasAnyDescendant(hasText(text(R.string.admin_approvals_account_line, text(R.string.role_merchant), "+50588880201"))))
            .assertIsDisplayed()
        compose.onNode(hasTestTag(ApprovalsTags.row("c1")) and hasAnyDescendant(hasText(text(R.string.admin_approvals_account_line, text(R.string.role_courier), "+50588880201"))))
            .assertIsDisplayed()
        compose.onNode(hasTestTag(ApprovalsTags.row("c1")) and hasAnyDescendant(hasText("c1@otli.test"))).assertIsDisplayed()
    }

    @Test
    fun theThreeSectionsHaveTheirTitles() {
        show(everything)

        compose.onNodeWithText(text(R.string.admin_approvals_pending)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_approvals_active)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_approvals_suspended)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_approvals_none_pending)).assertDoesNotExist()
    }

    @Test
    fun anEmptyQueueIsSaidAndTheOtherSectionsStillShow() {
        show(everything.copy(pending = emptyList()))

        compose.onNodeWithText(text(R.string.admin_approvals_none_pending)).assertIsDisplayed()
        compose.onNodeWithText("Comedor Marta").assertIsDisplayed()
    }

    @Test
    fun withNoAccountsAtAllItSaysSo() {
        show(ApprovalsUiState(isLoading = false))

        compose.onNodeWithText(text(R.string.admin_approvals_none_pending)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_approvals_active)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.admin_approvals_suspended)).assertDoesNotExist()
    }

    @Test
    fun aPendingAccountOffersApproveAndAnActiveOneSuspendAndASuspendedOneReactivate() {
        val events = show(everything)

        compose.onNodeWithTag(ApprovalsTags.approve("m-new")).performClick()
        compose.onNodeWithTag(ApprovalsTags.suspend("m1")).performClick()
        compose.onNodeWithTag(ApprovalsTags.approve("c1")).performClick()

        assertThat(events.approved).containsExactly("m-new", "c1").inOrder()
        assertThat(events.suspended).containsExactly("m1")
        compose.onNodeWithTag(ApprovalsTags.approve("m-new")).assertTextEquals(text(R.string.admin_approve))
        compose.onNodeWithTag(ApprovalsTags.suspend("m1")).assertTextEquals(text(R.string.admin_suspend))
        compose.onNodeWithTag(ApprovalsTags.approve("c1")).assertTextEquals(text(R.string.admin_reactivate))
    }

    @Test
    fun aPendingAccountCannotBeSuspendedFromTheList() {
        show(everything)

        compose.onNodeWithTag(ApprovalsTags.suspend("m-new")).assertDoesNotExist()
        compose.onNodeWithTag(ApprovalsTags.approve("m1")).assertDoesNotExist()
    }

    @Test
    fun whileAnActionRunsEveryButtonIsDisabled() {
        show(everything.copy(busyUid = "m-new"))

        compose.onNodeWithTag(ApprovalsTags.approve("m-new")).assertIsNotEnabled()
        compose.onNodeWithTag(ApprovalsTags.approve("c-new")).assertIsNotEnabled()
        compose.onNodeWithTag(ApprovalsTags.suspend("m1")).assertIsNotEnabled()
        compose.onNodeWithTag(ApprovalsTags.approve("c1")).assertIsNotEnabled()
    }

    @Test
    fun theButtonsAreEnabledWhenNothingRuns() {
        show(everything)

        compose.onNodeWithTag(ApprovalsTags.approve("m-new")).assertIsEnabled()
        compose.onNodeWithTag(ApprovalsTags.suspend("m1")).assertIsEnabled()
    }

    @Test
    fun anErrorIsShownAndCanBeDismissed() {
        val events = show(everything.copy(error = ApprovalError.ACTION_FAILED))

        compose.onNodeWithText(text(R.string.admin_approvals_action_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_dismiss)).performClick()

        assertThat(events.dismissed).isEqualTo(1)
    }

    @Test
    fun noErrorIsShownWhenThereIsNone() {
        show(everything)

        compose.onNodeWithText(text(R.string.admin_approvals_action_failed)).assertDoesNotExist()
    }
}
