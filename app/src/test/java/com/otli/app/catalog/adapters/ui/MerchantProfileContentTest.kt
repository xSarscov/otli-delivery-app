package com.otli.app.catalog.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Tall screen: the form scrolls, and off-screen rows are not "displayed" for the assertions.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class MerchantProfileContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private val loaded = MerchantProfileUiState(
        isLoading = false,
        name = "Comedor Marta",
        description = "Comida tipica",
        phone = "+50588880101",
        isOpen = true,
    )

    // A test may show several states in a row, so content is set once and driven by this state.
    private val current = mutableStateOf(loaded)
    private var contentSet = false
    private var onSave: () -> Unit = {}
    private var onOpenChange: (Boolean) -> Unit = {}
    private var onPickPhoto: () -> Unit = {}

    private fun show(
        state: MerchantProfileUiState,
        onSave: () -> Unit = {},
        onOpenChange: (Boolean) -> Unit = {},
        onPickPhoto: () -> Unit = {},
    ) {
        this.onSave = onSave
        this.onOpenChange = onOpenChange
        this.onPickPhoto = onPickPhoto
        current.value = state
        if (contentSet) return
        contentSet = true
        compose.setContent {
            MerchantProfileContent(
                state = current.value,
                onNameChange = {},
                onDescriptionChange = {},
                onPhoneChange = {},
                onOpenChange = { this.onOpenChange(it) },
                onPickPhoto = { this.onPickPhoto() },
                onSave = { this.onSave() },
            )
        }
    }

    @Test
    fun showsTheLoadedProfileFields() {
        show(loaded)

        compose.onNodeWithText("Comedor Marta").assertIsDisplayed()
        compose.onNodeWithText("Comida tipica").assertIsDisplayed()
        compose.onNodeWithText("+50588880101").assertIsDisplayed()
    }

    @Test
    fun whileLoadingItShowsNoForm() {
        show(MerchantProfileUiState(isLoading = true))
        compose.onNodeWithText(text(R.string.action_save)).assertDoesNotExist()
    }

    @Test
    fun aMissingMerchantDocumentShowsAMessageAndNoForm() {
        show(MerchantProfileUiState(isLoading = false, merchantMissing = true))

        compose.onNodeWithText(text(R.string.merchant_profile_missing)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_save)).assertDoesNotExist()
    }

    @Test
    fun savePhotoAndOpenToggleInvokeTheirCallbacks() {
        var saved = false
        var picked = false
        var openChoice: Boolean? = null
        show(loaded, onSave = { saved = true }, onOpenChange = { openChoice = it }, onPickPhoto = { picked = true })

        compose.onNodeWithText(text(R.string.action_save)).performClick()
        compose.onNodeWithText(text(R.string.action_choose_photo)).performClick()
        compose.onNodeWithText(text(R.string.label_store_open)).performClick()

        assertThat(saved).isTrue()
        assertThat(picked).isTrue()
        assertThat(openChoice).isFalse()
    }

    @Test
    fun aClosedStoreOffersToOpen() {
        var openChoice: Boolean? = null
        show(loaded.copy(isOpen = false), onOpenChange = { openChoice = it })

        compose.onNodeWithText(text(R.string.label_store_open)).performClick()

        assertThat(openChoice).isTrue()
    }

    @Test
    fun showsAMessageForEachError() {
        val cases = mapOf(
            MerchantProfileError.NAME_REQUIRED to R.string.merchant_profile_error_name,
            MerchantProfileError.INVALID_PHONE to R.string.register_error_invalid_phone,
            MerchantProfileError.SAVE_FAILED to R.string.merchant_profile_error_save,
            MerchantProfileError.TOGGLE_FAILED to R.string.merchant_profile_error_toggle,
            MerchantProfileError.PHOTO_FAILED to R.string.merchant_profile_error_photo,
        )
        for ((error, message) in cases) {
            show(loaded.copy(error = error))
            compose.onNodeWithText(text(message)).assertIsDisplayed()
        }
    }

    @Test
    fun showsThePhotoStatusAndTheSavedNotice() {
        show(loaded.copy(photoVersion = 0))
        compose.onNodeWithText(text(R.string.merchant_photo_none)).assertIsDisplayed()
        show(loaded.copy(photoVersion = 2, justSaved = true))
        compose.onNodeWithText(text(R.string.merchant_photo_set)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.merchant_profile_saved)).assertIsDisplayed()
    }

    @Test
    fun saveAndPhotoButtonsAreDisabledWhileBusy() {
        show(loaded.copy(isSaving = true))
        compose.onNodeWithText(text(R.string.action_save)).assertIsNotEnabled()

        show(loaded.copy(isUploadingPhoto = true))
        compose.onNodeWithText(text(R.string.action_choose_photo)).assertIsNotEnabled()
    }
}
