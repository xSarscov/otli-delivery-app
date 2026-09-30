package com.otli.app.catalog.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The two destinations are fake slots: the real screens need Hilt and are covered by their own tests. */
@RunWith(RobolectricTestRunner::class)
class MerchantHomeContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun show(
        initial: MerchantTab = MerchantTab.CATALOG,
        onSelected: (MerchantTab) -> Unit = {},
        onSignOut: () -> Unit = {},
    ) {
        compose.setContent {
            var selected by remember { mutableStateOf(initial) }
            MerchantHomeContent(
                selected = selected,
                onSelect = {
                    selected = it
                    onSelected(it)
                },
                onSignOut = onSignOut,
                catalog = { Text("catalog-slot") },
                profile = { Text("profile-slot") },
            )
        }
    }

    @Test
    fun signingOutFromTheAppBarMenuIsReportedOnEitherTab() {
        var signOuts = 0
        show(onSignOut = { signOuts++ })

        compose.onNodeWithContentDescription(text(R.string.action_more_options)).performClick()
        compose.onNodeWithText(text(R.string.action_logout)).performClick()
        compose.onNodeWithText(text(R.string.tab_profile)).performClick()
        compose.onNodeWithContentDescription(text(R.string.action_more_options)).performClick()
        compose.onNodeWithText(text(R.string.action_logout)).performClick()

        assertThat(signOuts).isEqualTo(2)
        compose.onNodeWithText("profile-slot").assertIsDisplayed()
    }

    @Test
    fun theAppBarShowsTheAppNameAboveTheTabs() {
        show()

        compose.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
    }

    @Test
    fun opensOnTheCatalogTabShowingOnlyTheCatalog() {
        show()

        compose.onNodeWithText("catalog-slot").assertIsDisplayed()
        compose.onNodeWithText("profile-slot").assertDoesNotExist()
    }

    @Test
    fun selectingTheProfileTabSwapsTheContentAndReportsTheChoice() {
        val chosen = mutableListOf<MerchantTab>()
        show(onSelected = { chosen += it })

        compose.onNodeWithText(text(R.string.tab_profile)).performClick()

        compose.onNodeWithText("profile-slot").assertIsDisplayed()
        compose.onNodeWithText("catalog-slot").assertDoesNotExist()
        assertThat(chosen).containsExactly(MerchantTab.PROFILE)
    }

    @Test
    fun canSwitchBackToTheCatalog() {
        show(initial = MerchantTab.PROFILE)
        compose.onNodeWithText("profile-slot").assertIsDisplayed()

        compose.onNodeWithText(text(R.string.tab_catalog)).performClick()

        compose.onNodeWithText("catalog-slot").assertIsDisplayed()
    }

    @Test
    fun leavesSystemBarInsetsToTheRootSoTheTabsAreNotPaddedTwice() {
        show()
        val topWithoutInsets = compose.onNodeWithText("catalog-slot").getUnclippedBoundsInRoot().top
        val statusBar = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 96, 0, 0))
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, statusBar) }
        compose.waitForIdle()

        compose.onNodeWithText(text(R.string.tab_catalog)).assertIsDisplayed()
        assertThat(compose.onNodeWithText("catalog-slot").getUnclippedBoundsInRoot().top).isEqualTo(topWithoutInsets)
    }
}
