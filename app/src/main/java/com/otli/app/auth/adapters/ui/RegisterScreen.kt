package com.otli.app.auth.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Container: wires [RegisterViewModel] to the stateless [RegisterContent]. */
@Composable
fun RegisterScreen(
    modifier: Modifier = Modifier,
    onNavigateToLogin: (() -> Unit)? = null,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RegisterContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onPhoneChange = viewModel::onPhoneChange,
        onStoreNameChange = viewModel::onStoreNameChange,
        onPinChange = viewModel::onPinChange,
        onRoleSelected = viewModel::onRoleSelected,
        onSubmit = viewModel::submit,
        onNavigateToLogin = onNavigateToLogin,
        modifier = modifier,
    )
}
