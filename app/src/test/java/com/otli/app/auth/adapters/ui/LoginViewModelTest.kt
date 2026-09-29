package com.otli.app.auth.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repo = RecordingAuthRepository()
    private val viewModel = LoginViewModel(repo)

    @Test
    fun submitLogsInWithTheTrimmedEmailAndTheExactPassword() {
        viewModel.onEmailChange("  ana@otli.test ")
        viewModel.onPasswordChange(" secret1 ")
        viewModel.submit()

        assertThat(repo.logins).containsExactly("ana@otli.test" to " secret1 ")
        assertThat(viewModel.uiState.value.isSubmitting).isFalse()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun blankEmailOrPasswordShowsAnErrorWithoutCallingTheRepository() {
        viewModel.onEmailChange("ana@otli.test")
        viewModel.submit()
        assertThat(viewModel.uiState.value.error).isEqualTo(LoginError.MISSING_FIELDS)

        viewModel.onEmailChange("   ")
        viewModel.onPasswordChange("secret1")
        viewModel.submit()
        assertThat(viewModel.uiState.value.error).isEqualTo(LoginError.MISSING_FIELDS)

        assertThat(repo.logins).isEmpty()
    }

    @Test
    fun rejectedCredentialsShowAnErrorAndEditingClearsIt() {
        repo.loginOutcome = { Result.failure(IllegalArgumentException("invalid credential")) }
        viewModel.onEmailChange("ana@otli.test")
        viewModel.onPasswordChange("wrong-password")
        viewModel.submit()

        assertThat(viewModel.uiState.value.error).isEqualTo(LoginError.FAILED)
        assertThat(viewModel.uiState.value.isSubmitting).isFalse()

        viewModel.onPasswordChange("secret1")
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun secondSubmitWhileInFlightIsIgnored() {
        val gate = CompletableDeferred<Result<Unit>>()
        repo.loginOutcome = { gate.await() }
        viewModel.onEmailChange("ana@otli.test")
        viewModel.onPasswordChange("secret1")
        viewModel.submit()

        assertThat(viewModel.uiState.value.isSubmitting).isTrue()
        viewModel.submit()
        assertThat(repo.logins).hasSize(1)

        gate.complete(Result.success(Unit))
        assertThat(viewModel.uiState.value.isSubmitting).isFalse()
    }
}
