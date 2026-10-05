package com.otli.app.admin.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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

/** The four destinations are fake slots: the real screens need Hilt and are covered by their own tests. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class AdminHomeContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun show(
        initial: AdminTab = AdminTab.ACTIVE,
        onSelected: (AdminTab) -> Unit = {},
        onSignOut: () -> Unit = {},
    ) {
        compose.setContent {
            var selected by remember { mutableStateOf(initial) }
            AdminHomeContent(
                selected = selected,
                onSelect = {
                    selected = it
                    onSelected(it)
                },
                onSignOut = onSignOut,
                active = { Text("active-slot") },
                orders = { Text("orders-slot") },
                accounts = { Text("accounts-slot") },
                fee = { Text("fee-slot") },
            )
        }
    }

    private val slots = mapOf(
        AdminTab.ACTIVE to "active-slot",
        AdminTab.ORDERS to "orders-slot",
        AdminTab.ACCOUNTS to "accounts-slot",
        AdminTab.FEE to "fee-slot",
    )

    private val titles = mapOf(
        AdminTab.ACTIVE to R.string.admin_tab_active,
        AdminTab.ORDERS to R.string.admin_tab_orders,
        AdminTab.ACCOUNTS to R.string.admin_tab_accounts,
        AdminTab.FEE to R.string.admin_tab_fee,
    )

    @Test
    fun everyTabHasATitleAndASlot() {
        assertThat(slots.keys).containsExactlyElementsIn(AdminTab.entries)
        assertThat(titles.keys).containsExactlyElementsIn(AdminTab.entries)
    }

    @Test
    fun onlyTheSelectedTabsSlotIsShown() {
        for (tab in AdminTab.entries) {
            show(initial = tab)

            compose.onNodeWithText(slots.getValue(tab)).assertIsDisplayed()
            for (other in AdminTab.entries - tab) compose.onNodeWithText(slots.getValue(other)).assertDoesNotExist()
            compose.activityRule.scenario.recreate()
        }
    }

    @Test
    fun tappingATabSelectsItAndShowsItsSlot() {
        val selected = mutableListOf<AdminTab>()
        show(onSelected = { selected += it })

        compose.onNodeWithText(text(R.string.admin_tab_fee)).performClick()
        compose.onNodeWithText(text(R.string.admin_tab_accounts)).performClick()
        compose.onNodeWithText(text(R.string.admin_tab_orders)).performClick()

        assertThat(selected).containsExactly(AdminTab.FEE, AdminTab.ACCOUNTS, AdminTab.ORDERS).inOrder()
        compose.onNodeWithText("orders-slot").assertIsDisplayed()
        compose.onNodeWithText("active-slot").assertDoesNotExist()
    }

    @Test
    fun theSelectedTabIsMarkedSelectedAndTheOthersAreNot() {
        show(initial = AdminTab.ACCOUNTS)

        compose.onNodeWithText(text(R.string.admin_tab_accounts)).assertIsSelected()
        compose.onNodeWithText(text(R.string.admin_tab_active)).assertIsNotSelected()
        compose.onNodeWithText(text(R.string.admin_tab_orders)).assertIsNotSelected()
        compose.onNodeWithText(text(R.string.admin_tab_fee)).assertIsNotSelected()
    }

    @Test
    fun signingOutFromTheAppBarMenuIsReported() {
        var signOuts = 0
        show(onSignOut = { signOuts++ })

        compose.onNodeWithContentDescription(text(R.string.action_more_options)).performClick()
        compose.onNodeWithText(text(R.string.action_logout)).performClick()

        assertThat(signOuts).isEqualTo(1)
    }
}
