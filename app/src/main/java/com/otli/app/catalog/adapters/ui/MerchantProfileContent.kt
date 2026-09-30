package com.otli.app.catalog.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.core.theme.OtliTheme

/** Stateless merchant profile form: name, description, phone, photo and the open/closed switch. */
@Composable
fun MerchantProfileContent(
    state: MerchantProfileUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onOpenChange: (Boolean) -> Unit,
    onPickPhoto: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.merchantMissing -> Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.merchant_profile_missing))
        }
        else -> Column(
            modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.merchant_profile_title), style = MaterialTheme.typography.headlineMedium)
            OpenSwitch(isOpen = state.isOpen, onOpenChange = onOpenChange)
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.label_store_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.label_store_description)) },
                minLines = 2,
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
            Text(
                stringResource(if (state.photoVersion > 0) R.string.merchant_photo_set else R.string.merchant_photo_none),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onPickPhoto, enabled = !state.isUploadingPhoto, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_choose_photo))
            }
            state.error?.let {
                Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error)
            }
            if (state.justSaved) Text(stringResource(R.string.merchant_profile_saved))
            Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun OpenSwitch(isOpen: Boolean, onOpenChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = isOpen, role = Role.Switch, onValueChange = onOpenChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.label_store_open))
        Switch(checked = isOpen, onCheckedChange = null)
    }
}

private fun MerchantProfileError.messageRes(): Int = when (this) {
    MerchantProfileError.NAME_REQUIRED -> R.string.merchant_profile_error_name
    MerchantProfileError.INVALID_PHONE -> R.string.register_error_invalid_phone
    MerchantProfileError.SAVE_FAILED -> R.string.merchant_profile_error_save
    MerchantProfileError.TOGGLE_FAILED -> R.string.merchant_profile_error_toggle
    MerchantProfileError.PHOTO_FAILED -> R.string.merchant_profile_error_photo
}

@Preview(showBackground = true)
@Composable
private fun MerchantProfileContentPreview() {
    OtliTheme {
        MerchantProfileContent(
            state = MerchantProfileUiState(isLoading = false, name = "Comedor Marta", isOpen = true),
            onNameChange = {},
            onDescriptionChange = {},
            onPhoneChange = {},
            onOpenChange = {},
            onPickPhoto = {},
            onSave = {},
        )
    }
}
