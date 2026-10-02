package com.otli.app.tracking.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.tracking.domain.TrackingPlan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackingStatusContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun show(plan: TrackingPlan, onAllowLocation: () -> Unit = {}) {
        compose.setContent { TrackingStatusContent(plan = plan, onAllowLocation = onAllowLocation) }
    }

    @Test
    fun whileSharingTheCourierSeesThatTheCustomerIsFollowingThem() {
        show(TrackingPlan.Share("o1", "courier-1"))

        compose.onNodeWithText(text(R.string.tracking_sharing)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tracking_permission_missing)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tracking_allow_location)).assertDoesNotExist()
    }

    @Test
    fun withoutThePermissionTheCourierSeesTheWarningAndCanAllowLocation() {
        var allowed = 0
        show(TrackingPlan.NeedsPermission("o1"), onAllowLocation = { allowed++ })

        compose.onNodeWithText(text(R.string.tracking_permission_missing)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.tracking_sharing)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tracking_allow_location)).performClick()

        assertThat(allowed).isEqualTo(1)
    }

    @Test
    fun withoutADeliveryNothingIsShown() {
        show(TrackingPlan.Idle)

        compose.onNodeWithText(text(R.string.tracking_sharing)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tracking_permission_missing)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.tracking_allow_location)).assertDoesNotExist()
    }
}
