package com.otli.app.auth.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginError { MISSING_FIELDS, FAILED }

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: LoginError? = null,
)

/**
 * A successful login needs no navigation effect: the auth state change makes the
 * session gate route the user to their home, pending or suspended screen.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun submit() {
        val form = _uiState.value
        if (form.isSubmitting) return
        val email = form.email.trim()
        if (email.isEmpty() || form.password.isEmpty()) {
            _uiState.update { it.copy(error = LoginError.MISSING_FIELDS) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = repository.login(email, form.password)
            _uiState.update {
                it.copy(isSubmitting = false, error = if (result.isFailure) LoginError.FAILED else null)
            }
        }
    }
}
