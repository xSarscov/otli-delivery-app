package com.otli.app.auth.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.auth.domain.Role
import com.otli.app.core.map.MapPin
import com.otli.app.core.map.MapPinPicker
import com.otli.app.core.map.NativePinMapContent
import com.otli.app.core.map.PinMapContent
import com.otli.app.core.theme.OtliTheme
import androidx.compose.ui.semantics.Role as SemanticsRole

/** Stateless registration form; [onNavigateToLogin] hides the sign-in link when null. */
@Composable
fun RegisterContent(
    state: RegisterUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onStoreNameChange: (String) -> Unit,
    onPinChange: (MapPin?) -> Unit,
    onRoleSelected: (Role) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToLogin: (() -> Unit)?,
    modifier: Modifier = Modifier,
    mapContent: PinMapContent = NativePinMapContent,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.register_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            label = { Text(stringResource(R.string.label_display_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.label_email)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.label_phone)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.label_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        RolePicker(state.availableRoles, selected = state.role, onRoleSelected = onRoleSelected)
        if (state.role == Role.MERCHANT) {
            OutlinedTextField(
                value = state.storeName,
                onValueChange = onStoreNameChange,
                label = { Text(stringResource(R.string.label_store_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.label_store_location), style = MaterialTheme.typography.titleSmall)
            MapPinPicker(pin = state.pin, onPinChange = onPinChange, mapContent = mapContent)
        }
        state.error?.let {
            Text(
                stringResource(it.messageRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(onClick = onSubmit, enabled = !state.isSubmitting, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_register))
        }
        onNavigateToLogin?.let {
            TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.register_have_account))
            }
        }
    }
}

@Composable
private fun RolePicker(roles: List<Role>, selected: Role, onRoleSelected: (Role) -> Unit) {
    Text(stringResource(R.string.label_role), style = MaterialTheme.typography.titleSmall)
    Column(Modifier.selectableGroup()) {
        for (role in roles) {
            Row(
                Modifier.fillMaxWidth().selectable(
                    selected = role == selected,
                    onClick = { onRoleSelected(role) },
                    role = SemanticsRole.RadioButton,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = role == selected, onClick = null)
                Text(stringResource(role.labelRes()), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private fun RegisterError.messageRes(): Int = when (this) {
    RegisterError.MISSING_FIELDS -> R.string.register_error_missing_fields
    RegisterError.WEAK_PASSWORD -> R.string.register_error_weak_password
    RegisterError.STORE_NAME_REQUIRED -> R.string.register_error_store_name_required
    RegisterError.INVALID_PHONE -> R.string.register_error_invalid_phone
    RegisterError.PIN_REQUIRED -> R.string.register_error_pin_required
    RegisterError.FAILED -> R.string.register_error_failed
}

@Preview(showBackground = true)
@Composable
private fun RegisterContentPreview() {
    OtliTheme {
        RegisterContent(RegisterUiState(), {}, {}, {}, {}, {}, {}, {}, {}, onNavigateToLogin = {})
    }
}
