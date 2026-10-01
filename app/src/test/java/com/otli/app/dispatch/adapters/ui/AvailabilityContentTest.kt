package com.otli.app.dispatch.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class AvailabilityContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private class Events {
        val requested = mutableListOf<Boolean>()
        var dismissed = 0
    }

    private fun show(state: AvailabilityUiState): Events {
        val events = Events()
        compose.setContent {
            AvailabilityContent(
                state = state,
                onSetOnline = { events.requested += it },
                onDismissError = { events.dismissed++ },
            )
        }
        return events
    }

    private fun loaded(isOnline: Boolean = false, hasActiveOrder: Boolean = false) =
        AvailabilityUiState(isLoading = false, isOnline = isOnline, hasActiveOrder = hasActiveOrder)

    @Test
    fun anOfflineCourierSeesTheSwitchOffAndCanGoOnline() {
        val events = show(loaded(isOnline = false))

        compose.onNodeWithText(text(R.string.availability_offline)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.availability_online)).assertDoesNotExist()
        compose.onNodeWithTag(AvailabilityTags.SWITCH).assertIsOff().assertIsEnabled().performClick()

        assertThat(events.requested).containsExactly(true)
    }

    @Test
    fun anOnlineCourierSeesTheSwitchOnAndCanGoOffline() {
        val events = show(loaded(isOnline = true))

        compose.onNodeWithText(text(R.string.availability_online)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.availability_offline)).assertDoesNotExist()
        compose.onNodeWithTag(AvailabilityTags.SWITCH).assertIsOn().assertIsEnabled().performClick()

        assertThat(events.requested).containsExactly(false)
    }

    @Test
    fun aCourierWithAnActiveOrderCannotGoOfflineAndIsToldWhy() {
        val events = show(loaded(isOnline = true, hasActiveOrder = true))

        compose.onNodeWithTag(AvailabilityTags.SWITCH).assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.availability_locked)).assertIsDisplayed()
        assertThat(events.requested).isEmpty()
    }

    @Test
    fun withoutAnActiveOrderThereIsNoLockedHint() {
        show(loaded(isOnline = true))

        compose.onNodeWithText(text(R.string.availability_locked)).assertDoesNotExist()
    }

    @Test
    fun theSwitchIsLockedWhileLoading() {
        show(AvailabilityUiState())
        compose.onNodeWithTag(AvailabilityTags.SWITCH).assertIsNotEnabled()
    }

    @Test
    fun theSwitchIsLockedWhileAChangeIsInFlight() {
        show(loaded().copy(isUpdating = true))

        compose.onNodeWithTag(AvailabilityTags.SWITCH).assertIsNotEnabled()
    }

    @Test
    fun aLoadErrorIsShownAndCanBeDismissed() {
        val events = show(loaded().copy(error = AvailabilityError.LOAD_FAILED))

        compose.onNodeWithText(text(R.string.availability_error_load)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.availability_error_update)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.action_dismiss)).performClick()

        assertThat(events.dismissed).isEqualTo(1)
    }

    @Test
    fun anUpdateErrorHasItsOwnMessage() {
        show(loaded().copy(error = AvailabilityError.UPDATE_FAILED))

        compose.onNodeWithText(text(R.string.availability_error_update)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.availability_error_load)).assertDoesNotExist()
    }
}
