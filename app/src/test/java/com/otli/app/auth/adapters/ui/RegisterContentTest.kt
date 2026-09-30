package com.otli.app.auth.adapters.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.auth.domain.Role
import com.otli.app.core.map.MapPin
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Tall screen: the form scrolls, and off-screen rows are not "displayed" for the assertions.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h1600dp")
class RegisterContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    private fun show(
        state: RegisterUiState,
        onSubmit: () -> Unit = {},
        onRoleSelected: (Role) -> Unit = {},
        onPinChange: (MapPin?) -> Unit = {},
    ) {
        compose.setContent {
            RegisterContent(
                state = state,
                onEmailChange = {},
                onPasswordChange = {},
                onDisplayNameChange = {},
                onPhoneChange = {},
                onStoreNameChange = {},
                onPinChange = onPinChange,
                onRoleSelected = onRoleSelected,
                onSubmit = onSubmit,
                onNavigateToLogin = null,
                // Native MapLibre cannot render on the JVM; a labelled stand-in proves the slot is used.
                mapContent = { _, _ -> Text("fake-map") },
            )
        }
    }

    @Test
    fun rolePickerShowsTheThreeSelfRegistrableRolesAndNotAdmin() {
        show(RegisterUiState())

        compose.onNodeWithText(text(R.string.role_customer)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.role_merchant)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.role_courier)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.role_admin)).assertDoesNotExist()
    }

    @Test
    fun tappingARoleAndTheSubmitButtonInvokesTheCallbacks() {
        var selected: Role? = null
        var submitted = false
        show(RegisterUiState(), onSubmit = { submitted = true }, onRoleSelected = { selected = it })

        compose.onNodeWithText(text(R.string.role_courier)).performClick()
        compose.onNodeWithText(text(R.string.action_register)).performClick()

        assertThat(selected).isEqualTo(Role.COURIER)
        assertThat(submitted).isTrue()
    }

    @Test
    fun showsTheErrorMessageForTheCurrentError() {
        show(RegisterUiState(error = RegisterError.WEAK_PASSWORD))

        compose.onNodeWithText(text(R.string.register_error_weak_password)).assertIsDisplayed()
    }

    @Test
    fun merchantRoleShowsStoreNameAndTheMapPinPicker() {
        show(RegisterUiState(role = Role.MERCHANT))

        compose.onNodeWithText(text(R.string.label_store_name)).assertIsDisplayed()
        compose.onNodeWithText("fake-map").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.map_attribution)).assertIsDisplayed()
    }

    @Test
    fun customerAndCourierRolesShowNoStoreFieldsOrMap() {
        show(RegisterUiState(role = Role.CUSTOMER))
        compose.onNodeWithText(text(R.string.label_store_name)).assertDoesNotExist()
        compose.onNodeWithText("fake-map").assertDoesNotExist()
    }

    @Test
    fun showsTheMessageForEachMerchantStoreError() {
        show(RegisterUiState(role = Role.MERCHANT, error = RegisterError.PIN_REQUIRED))
        compose.onNodeWithText(text(R.string.register_error_pin_required)).assertIsDisplayed()
    }
}
