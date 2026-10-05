package com.otli.app.admin.adapters.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.catalog.domain.PriceInput
import com.otli.app.core.money.Money
import com.otli.app.core.theme.OtliTheme

object FeeSettingsTags {
    const val LOADING = "fee-settings-loading"
    const val FIELD = "fee-settings-field"
}

/** The wording of each way a fee save can end. */
object FeeSettingsMessages {
    @StringRes
    fun of(result: FeeSaveResult): Int = when (result) {
        FeeSaveResult.SAVED -> R.string.admin_fee_saved
        FeeSaveResult.INVALID_AMOUNT -> R.string.admin_fee_invalid
        FeeSaveResult.NOT_POSITIVE -> R.string.admin_fee_not_positive
        FeeSaveResult.SAVE_FAILED -> R.string.admin_fee_save_failed
    }
}

/** Stateless fee editor: the current fee, one amount field and Save. */
@Composable
fun FeeSettingsContent(
    state: FeeSettingsUiState,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag(FeeSettingsTags.LOADING))
            state.loadFailed -> Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.admin_fee_load_failed), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
            else -> Editor(state, onInputChange, onSave)
        }
    }
}

@Composable
private fun Editor(state: FeeSettingsUiState, onInputChange: (String) -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.admin_fee_title), style = MaterialTheme.typography.titleLarge)
        state.currentFee?.let { Text(stringResource(R.string.admin_fee_current, PriceInput.format(it)), style = MaterialTheme.typography.bodyLarge) }
        Text(stringResource(R.string.admin_fee_hint), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            label = { Text(stringResource(R.string.admin_fee_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag(FeeSettingsTags.FIELD),
        )
        state.result?.let { result ->
            val color = if (result == FeeSaveResult.SAVED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Text(stringResource(FeeSettingsMessages.of(result)), color = color, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.admin_fee_save))
        }
    }
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun FeeSettingsPreview() {
    OtliTheme {
        FeeSettingsContent(
            state = FeeSettingsUiState(isLoading = false, currentFee = Money(3000), input = "30.00", result = FeeSaveResult.SAVED),
            onInputChange = {},
            onSave = {},
            onRetry = {},
        )
    }
}
