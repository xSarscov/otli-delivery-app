package com.otli.app.admin.adapters.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.otli.app.R
import com.otli.app.admin.application.CancelOrder
import com.otli.app.ordering.domain.Order

/** What the cancellation reason dialog needs: the order, whether the last reason was refused, and what to do next. */
data class CancelDialogState(
    val order: Order,
    val reasonInvalid: Boolean,
    val busy: Boolean,
    val onConfirm: (String) -> Unit,
    val onDismiss: () -> Unit,
)

object CancelReasonTags {
    const val FIELD = "cancel-reason-field"
}

/**
 * Asks Admin why an order is cancelled; the customer is shown the answer. The field holds the text
 * itself and never grows past the length the rules accept; Cancel needs a non-blank reason and is off
 * while the cancellation is being written. Verified on a device: unit tests cannot host a text field in a dialog.
 */
@Composable
fun CancelReasonDialog(state: CancelDialogState) {
    var reason by rememberSaveable(state.order.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = state.onDismiss,
        title = { Text(stringResource(R.string.admin_cancel_title)) },
        text = {
            Column {
                Text(stringResource(R.string.admin_cancel_message, state.order.merchantName))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(CancelOrder.MAX_REASON_LENGTH) },
                    label = { Text(stringResource(R.string.admin_cancel_reason_label)) },
                    isError = state.reasonInvalid,
                    supportingText = if (state.reasonInvalid) {
                        { Text(stringResource(R.string.admin_cancel_reason_invalid)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth().testTag(CancelReasonTags.FIELD),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { state.onConfirm(reason) }, enabled = reason.isNotBlank() && !state.busy) {
                Text(stringResource(R.string.admin_cancel_order))
            }
        },
        dismissButton = { TextButton(onClick = state.onDismiss) { Text(stringResource(R.string.admin_cancel_keep)) } },
    )
}
