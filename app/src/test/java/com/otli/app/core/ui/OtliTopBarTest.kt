package com.otli.app.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OtliTopBarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    @Test
    fun showsTheTitle() {
        compose.setContent { OtliTopBar(title = "Comedor Marta") }

        compose.onNodeWithText("Comedor Marta").assertIsDisplayed()
    }

    @Test
    fun anotherTitleReplacesTheFirst() {
        compose.setContent { OtliTopBar(title = "Pulperia Sol") }

        compose.onNodeWithText("Pulperia Sol").assertIsDisplayed()
        compose.onNodeWithText("Comedor Marta").assertDoesNotExist()
    }

    @Test
    fun withoutAnUpCallbackThereIsNoUpArrow() {
        compose.setContent { OtliTopBar(title = "Otli") }

        compose.onNodeWithContentDescription(text(R.string.action_back)).assertDoesNotExist()
    }

    @Test
    fun theUpArrowReportsEachTap() {
        var ups = 0
        compose.setContent { OtliTopBar(title = "Otli", onBack = { ups++ }) }

        compose.onNodeWithContentDescription(text(R.string.action_back)).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        assertThat(ups).isEqualTo(2)
    }

    @Test
    fun hostsTheActionsItIsGiven() {
        compose.setContent { OtliTopBar(title = "Otli", actions = { Text("action-slot") }) }

        compose.onNodeWithText("action-slot").assertIsDisplayed()
    }

    @Test
    fun leavesSystemBarInsetsToTheRootSoTheBarIsNotPaddedTwice() {
        compose.setContent { OtliTopBar(title = "Otli") }
        val statusBar = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 96, 0, 0))
            .build()
        compose.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, statusBar) }
        compose.waitForIdle()

        compose.onNodeWithText("Otli").assertIsDisplayed()
        assertThat(compose.onNodeWithText("Otli").getUnclippedBoundsInRoot().top).isLessThan(96.dp)
    }
}
