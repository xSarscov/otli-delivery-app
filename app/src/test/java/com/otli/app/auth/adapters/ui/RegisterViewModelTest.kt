package com.otli.app.auth.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.domain.MerchantStoreDetails
import com.otli.app.auth.domain.ProfileFields
import com.otli.app.auth.domain.Role
import com.otli.app.core.map.MapPin
import com.otli.app.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class RegisterViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repo = RecordingAuthRepository()
    private val viewModel = RegisterViewModel(repo)

    private fun fillValidForm(role: Role = Role.CUSTOMER) = viewModel.apply {
        onEmailChange("  ana@otli.test ")
        onPasswordChange("secret1")
        onDisplayNameChange(" Ana Lopez ")
        onPhoneChange("8888-0000")
        onRoleSelected(role)
    }

    @Test
    fun rolePickerOffersCustomerMerchantAndCourierButNeverAdmin() {
        val offered = viewModel.uiState.value.availableRoles

        assertThat(offered).containsExactly(Role.CUSTOMER, Role.MERCHANT, Role.COURIER).inOrder()
        assertThat(offered).doesNotContain(Role.ADMIN)
    }

    @Test
    fun selectingAdminIsIgnoredAndKeepsThePreviousRole() {
        viewModel.onRoleSelected(Role.COURIER)
        viewModel.onRoleSelected(Role.ADMIN)

        assertThat(viewModel.uiState.value.role).isEqualTo(Role.COURIER)
    }

    @Test
    fun submitRegistersWithTheChosenRoleAndTrimmedFields() {
        fillValidForm(Role.COURIER).submit()

        assertThat(repo.registrations).containsExactly(
            RecordingAuthRepository.Registration(
                email = "ana@otli.test",
                password = "secret1",
                role = Role.COURIER,
                profile = ProfileFields(displayName = "Ana Lopez", phone = "8888-0000"),
            ),
        )
        assertThat(viewModel.uiState.value.isSubmitting).isFalse()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun submitWithMissingFieldsShowsAnErrorWithoutCallingTheRepository() {
        fillValidForm().onDisplayNameChange("   ")
        viewModel.submit()

        assertThat(viewModel.uiState.value.error).isEqualTo(RegisterError.MISSING_FIELDS)
        assertThat(repo.registrations).isEmpty()
    }

    @Test
    fun submitWithAShortPasswordShowsAWeakPasswordError() {
        fillValidForm().onPasswordChange("12345")
        viewModel.submit()

        assertThat(viewModel.uiState.value.error).isEqualTo(RegisterError.WEAK_PASSWORD)
        assertThat(repo.registrations).isEmpty()
    }

    @Test
    fun repositoryFailureMapsToAnErrorAndEditingClearsIt() {
        repo.registerOutcome = { Result.failure(IllegalStateException("network")) }
        fillValidForm().submit()

        assertThat(viewModel.uiState.value.error).isEqualTo(RegisterError.FAILED)
        assertThat(viewModel.uiState.value.isSubmitting).isFalse()

        viewModel.onEmailChange("other@otli.test")
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun secondSubmitWhileInFlightIsIgnored() {
        val gate = CompletableDeferred<Result<Unit>>()
        repo.registerOutcome = { gate.await() }
        fillValidForm().submit()

        assertThat(viewModel.uiState.value.isSubmitting).isTrue()
        viewModel.submit()
        assertThat(repo.registrations).hasSize(1)

        gate.complete(Result.success(Unit))
        assertThat(viewModel.uiState.value.isSubmitting).isFalse()
    }

    private fun fillValidMerchantForm() = fillValidForm(Role.MERCHANT).apply {
        onStoreNameChange("  Pulperia Lopez ")
        onPinChange(MapPin(12.27, -86.57))
    }

    @Test
    fun merchantSubmitSendsNormalizedPhoneStoreNameAndPin() {
        fillValidMerchantForm().submit()

        assertThat(repo.registrations).containsExactly(
            RecordingAuthRepository.Registration(
                email = "ana@otli.test",
                password = "secret1",
                role = Role.MERCHANT,
                profile = ProfileFields(displayName = "Ana Lopez", phone = "+50588880000"),
                store = MerchantStoreDetails("Pulperia Lopez", "+50588880000", 12.27, -86.57),
            ),
        )
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun merchantSubmitWithoutAStoreNameIsRefusedAndNamesTheDetail() {
        fillValidMerchantForm().onStoreNameChange("   ")
        viewModel.submit()

        assertThat(viewModel.uiState.value.error).isEqualTo(RegisterError.STORE_NAME_REQUIRED)
        assertThat(repo.registrations).isEmpty()
    }

    @Test
    fun merchantSubmitWithAnInvalidNicaraguanPhoneIsRefused() {
        fillValidMerchantForm().onPhoneChange("1234")
        viewModel.submit()

        assertThat(viewModel.uiState.value.error).isEqualTo(RegisterError.INVALID_PHONE)
        assertThat(repo.registrations).isEmpty()
    }

    @Test
    fun merchantSubmitWithoutAPinIsRefused() {
        fillValidMerchantForm().onPinChange(null)
        viewModel.submit()

        assertThat(viewModel.uiState.value.error).isEqualTo(RegisterError.PIN_REQUIRED)
        assertThat(repo.registrations).isEmpty()
    }

    @Test
    fun customerAndCourierSubmitNeverNeedStoreDetailsOrAValidNicaraguanPhone() {
        fillValidForm(Role.CUSTOMER).onPhoneChange("+1 555 0100")
        viewModel.submit()

        assertThat(repo.registrations).hasSize(1)
        assertThat(repo.registrations.single().store).isNull()
        assertThat(repo.registrations.single().profile.phone).isEqualTo("+1 555 0100")
    }

    @Test
    fun switchingBackFromMerchantSendsNoStoreDetailsEvenIfTheyWereFilled() {
        fillValidMerchantForm().onRoleSelected(Role.CUSTOMER)
        viewModel.submit()

        assertThat(repo.registrations.single().store).isNull()
    }
}
