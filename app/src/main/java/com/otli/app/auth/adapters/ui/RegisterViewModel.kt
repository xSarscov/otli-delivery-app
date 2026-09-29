package com.otli.app.auth.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import com.otli.app.auth.domain.ProfileFields
import com.otli.app.auth.domain.RegistrationPolicy
import com.otli.app.auth.domain.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RegisterError { MISSING_FIELDS, WEAK_PASSWORD, FAILED }

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val phone: String = "",
    val role: Role = Role.CUSTOMER,
    /** Roles the picker offers; comes from the domain policy, so Admin can never appear. */
    val availableRoles: List<Role> = RegistrationPolicy.selfRegistrableRoles,
    val isSubmitting: Boolean = false,
    val error: RegisterError? = null,
)

/**
 * A successful registration needs no navigation effect: the auth state change makes
 * the session gate route the user to their home or pending screen.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) = edit { copy(email = value) }

    fun onPasswordChange(value: String) = edit { copy(password = value) }

    fun onDisplayNameChange(value: String) = edit { copy(displayName = value) }

    fun onPhoneChange(value: String) = edit { copy(phone = value) }

    fun onRoleSelected(role: Role) = edit { if (role in availableRoles) copy(role = role) else this }

    fun submit() {
        val form = _uiState.value
        if (form.isSubmitting) return
        val email = form.email.trim()
        val displayName = form.displayName.trim()
        val phone = form.phone.trim()
        val invalid = when {
            email.isEmpty() || form.password.isEmpty() || displayName.isEmpty() || phone.isEmpty() ->
                RegisterError.MISSING_FIELDS
            form.password.length < MIN_PASSWORD_LENGTH -> RegisterError.WEAK_PASSWORD
            else -> null
        }
        if (invalid != null) {
            _uiState.update { it.copy(error = invalid) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = repository.register(email, form.password, form.role, ProfileFields(displayName, phone))
            _uiState.update {
                it.copy(isSubmitting = false, error = if (result.isFailure) RegisterError.FAILED else null)
            }
        }
    }

    private fun edit(change: RegisterUiState.() -> RegisterUiState) =
        _uiState.update { it.change().copy(error = null) }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
    }
}
