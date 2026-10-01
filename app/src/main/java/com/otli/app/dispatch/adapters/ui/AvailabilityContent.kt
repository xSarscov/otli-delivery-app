package com.otli.app.dispatch.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.core.theme.OtliTheme

object AvailabilityTags {
    const val SWITCH = "availability-switch"
}

/** Stateless online/offline switch with the reason it may be locked and the last error. */
@Composable
fun AvailabilityContent(
    state: AvailabilityUiState,
    onSetOnline: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(if (state.isOnline) R.string.availability_online else R.string.availability_offline),
                style = MaterialTheme.typography.titleMedium,
            )
            Switch(
                checked = state.isOnline,
                onCheckedChange = onSetOnline,
                enabled = !state.isLoading && !state.isUpdating && !(state.isOnline && state.hasActiveOrder),
                modifier = Modifier.testTag(AvailabilityTags.SWITCH),
            )
        }
        if (state.hasActiveOrder) {
            Text(stringResource(R.string.availability_locked), style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let { error ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val message = if (error == AvailabilityError.LOAD_FAILED) R.string.availability_error_load else R.string.availability_error_update
                Text(stringResource(message), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDismissError) { Text(stringResource(R.string.action_dismiss)) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AvailabilityOnlineBusyPreview() {
    OtliTheme {
        AvailabilityContent(
            AvailabilityUiState(isLoading = false, isOnline = true, hasActiveOrder = true),
            onSetOnline = {},
            onDismissError = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AvailabilityOfflinePreview() {
    OtliTheme {
        AvailabilityContent(AvailabilityUiState(isLoading = false), onSetOnline = {}, onDismissError = {})
    }
}
