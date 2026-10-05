package com.otli.app.admin.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.core.money.Money
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp")
class FeeSettingsContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private class Events {
        val typed = mutableListOf<String>()
        var saves = 0
        var retries = 0
    }

    private fun show(state: FeeSettingsUiState, events: Events = Events()): Events {
        compose.setContent {
            FeeSettingsContent(
                state = state,
                onInputChange = { events.typed += it },
                onSave = { events.saves++ },
                onRetry = { events.retries++ },
            )
        }
        return events
    }

    private val loaded = FeeSettingsUiState(isLoading = false, currentFee = Money(3000), input = "30.00")

    @Test
    fun itShowsProgressWhileTheFeeLoads() {
        show(FeeSettingsUiState())

        compose.onNodeWithTag(FeeSettingsTags.LOADING).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_fee_save)).assertDoesNotExist()
    }

    @Test
    fun aFailedLoadOffersARetry() {
        val events = show(FeeSettingsUiState(isLoading = false, loadFailed = true))

        compose.onNodeWithText(text(R.string.admin_fee_load_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_retry)).performClick()

        assertThat(events.retries).isEqualTo(1)
    }

    @Test
    fun itShowsTheCurrentFeeAndWhatAChangeAppliesTo() {
        show(loaded)

        compose.onNodeWithText(text(R.string.admin_fee_current, "30.00")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_fee_hint)).assertIsDisplayed()
    }

    @Test
    fun typingReportsTheNewText() {
        val events = show(loaded.copy(input = ""))

        compose.onNodeWithTag(FeeSettingsTags.FIELD).performTextInput("45")

        assertThat(events.typed).containsExactly("45")
    }

    @Test
    fun savingReportsTheClick() {
        val events = show(loaded)

        compose.onNodeWithText(text(R.string.admin_fee_save)).performClick()

        assertThat(events.saves).isEqualTo(1)
    }

    @Test
    fun anEmptyFieldCannotBeSaved() {
        show(loaded.copy(input = ""))

        compose.onNodeWithText(text(R.string.admin_fee_save)).assertIsNotEnabled()
    }

    @Test
    fun theSaveButtonFollowsTheStateAndShowsWhileSaving() {
        show(loaded.copy(isSaving = true))
        compose.onNodeWithText(text(R.string.admin_fee_save)).assertIsNotEnabled()
    }

    @Test
    fun theSaveButtonIsEnabledWithAnAmountToSave() {
        show(loaded)

        compose.onNodeWithText(text(R.string.admin_fee_save)).assertIsEnabled()
    }

    @Test
    fun eachOutcomeHasItsOwnMessageAndAnOutcomeIsAbsentWhenThereIsNone() {
        val expected = mapOf(
            FeeSaveResult.SAVED to R.string.admin_fee_saved,
            FeeSaveResult.INVALID_AMOUNT to R.string.admin_fee_invalid,
            FeeSaveResult.NOT_POSITIVE to R.string.admin_fee_not_positive,
            FeeSaveResult.SAVE_FAILED to R.string.admin_fee_save_failed,
        )
        assertThat(expected.keys).containsExactlyElementsIn(FeeSaveResult.entries)
        for ((result, message) in expected) {
            assertThat(FeeSettingsMessages.of(result)).isEqualTo(message)
        }

        show(loaded.copy(result = FeeSaveResult.NOT_POSITIVE))
        compose.onNodeWithText(text(R.string.admin_fee_not_positive)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.admin_fee_saved)).assertDoesNotExist()
    }
}
