package com.otli.app.auth.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.domain.ProfileFields
import com.otli.app.auth.domain.Role
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
        fillValidForm(Role.MERCHANT).submit()

        assertThat(repo.registrations).containsExactly(
            RecordingAuthRepository.Registration(
                email = "ana@otli.test",
                password = "secret1",
                role = Role.MERCHANT,
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
}
