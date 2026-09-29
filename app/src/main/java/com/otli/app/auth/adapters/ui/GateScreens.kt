package com.otli.app.auth.adapters.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.otli.app.R
import com.otli.app.auth.domain.Role
import com.otli.app.core.theme.OtliTheme

/** Container for the pending-approval gate. */
@Composable
fun PendingApprovalScreen(role: Role, modifier: Modifier = Modifier, viewModel: GateViewModel = hiltViewModel()) =
    PendingApprovalContent(role, onLogout = viewModel::logout, modifier = modifier)

/** Container for the suspended-account gate. */
@Composable
fun SuspendedScreen(role: Role, modifier: Modifier = Modifier, viewModel: GateViewModel = hiltViewModel()) =
    SuspendedContent(role, onLogout = viewModel::logout, modifier = modifier)

@Composable
fun PendingApprovalContent(role: Role, onLogout: () -> Unit, modifier: Modifier = Modifier) =
    GateContent(R.string.gate_pending_title, role.pendingMessageRes(), onLogout, modifier)

@Composable
fun SuspendedContent(role: Role, onLogout: () -> Unit, modifier: Modifier = Modifier) =
    GateContent(R.string.gate_suspended_title, role.suspendedMessageRes(), onLogout, modifier)

@Composable
private fun GateContent(
    @StringRes title: Int,
    @StringRes message: Int,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onLogout) { Text(stringResource(R.string.action_logout)) }
    }
}

@StringRes
private fun Role.pendingMessageRes(): Int = when (this) {
    Role.MERCHANT -> R.string.gate_pending_merchant
    Role.COURIER -> R.string.gate_pending_courier
    Role.CUSTOMER, Role.ADMIN -> R.string.gate_pending_generic
}

@StringRes
private fun Role.suspendedMessageRes(): Int = when (this) {
    Role.CUSTOMER -> R.string.gate_suspended_customer
    Role.MERCHANT -> R.string.gate_suspended_merchant
    Role.COURIER -> R.string.gate_suspended_courier
    Role.ADMIN -> R.string.gate_suspended_generic
}

@Preview(showBackground = true)
@Composable
private fun PendingApprovalPreview() {
    OtliTheme { PendingApprovalContent(role = Role.MERCHANT, onLogout = {}) }
}

@Preview(showBackground = true)
@Composable
private fun SuspendedPreview() {
    OtliTheme { SuspendedContent(role = Role.COURIER, onLogout = {}) }
}
