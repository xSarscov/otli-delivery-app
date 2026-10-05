package com.otli.app.admin.adapters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.otli.app.auth.adapters.ui.labelRes
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import com.otli.app.core.theme.OtliTheme

object ApprovalsTags {
    const val LOADING = "approvals-loading"

    fun row(uid: String) = "approvals-row-$uid"

    /** Approves a pending account, or reactivates a suspended one. */
    fun approve(uid: String) = "approvals-approve-$uid"

    fun suspend(uid: String) = "approvals-suspend-$uid"
}

/** Stateless Admin list of merchant and courier accounts: the approval queue first, then active and suspended ones. */
@Composable
fun ApprovalsContent(
    state: ApprovalsUiState,
    onApprove: (UserAccount) -> Unit,
    onSuspend: (UserAccount) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag(ApprovalsTags.LOADING))
            state.loadFailed -> Text(
                stringResource(R.string.admin_approvals_load_failed),
                Modifier.align(Alignment.Center).padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> Accounts(state, onApprove, onSuspend, onDismissError)
        }
    }
}

@Composable
private fun Accounts(state: ApprovalsUiState, onApprove: (UserAccount) -> Unit, onSuspend: (UserAccount) -> Unit, onDismissError: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.error != null) item(key = "error") { ErrorBanner(onDismissError) }
        item(key = "title-pending") { Text(stringResource(R.string.admin_approvals_pending), style = MaterialTheme.typography.titleLarge) }
        if (state.pending.isEmpty()) {
            item(key = "none-pending") { Text(stringResource(R.string.admin_approvals_none_pending), style = MaterialTheme.typography.bodyMedium) }
        }
        accounts(state.pending, state.busyUid, onApprove, onSuspend)
        section(R.string.admin_approvals_active, state.active, state.busyUid, onApprove, onSuspend)
        section(R.string.admin_approvals_suspended, state.suspended, state.busyUid, onApprove, onSuspend)
    }
}

private fun LazyListScope.section(
    title: Int,
    list: List<UserAccount>,
    busyUid: String?,
    onApprove: (UserAccount) -> Unit,
    onSuspend: (UserAccount) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "title-$title") { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) }
    accounts(list, busyUid, onApprove, onSuspend)
}

private fun LazyListScope.accounts(
    list: List<UserAccount>,
    busyUid: String?,
    onApprove: (UserAccount) -> Unit,
    onSuspend: (UserAccount) -> Unit,
) {
    items(list, key = { it.uid }) { account -> AccountRow(account, busyUid != null, onApprove, onSuspend) }
}

@Composable
private fun AccountRow(account: UserAccount, actionRunning: Boolean, onApprove: (UserAccount) -> Unit, onSuspend: (UserAccount) -> Unit) {
    Card(Modifier.fillMaxWidth().testTag(ApprovalsTags.row(account.uid))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(account.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.admin_approvals_account_line, stringResource(account.role.labelRes()), account.phone),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(account.email, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (account.status) {
                    AccountStatus.PENDING -> Button(
                        onClick = { onApprove(account) },
                        enabled = !actionRunning,
                        modifier = Modifier.testTag(ApprovalsTags.approve(account.uid)),
                    ) { Text(stringResource(R.string.admin_approve)) }
                    AccountStatus.ACTIVE -> OutlinedButton(
                        onClick = { onSuspend(account) },
                        enabled = !actionRunning,
                        modifier = Modifier.testTag(ApprovalsTags.suspend(account.uid)),
                    ) { Text(stringResource(R.string.admin_suspend)) }
                    AccountStatus.SUSPENDED -> Button(
                        onClick = { onApprove(account) },
                        enabled = !actionRunning,
                        modifier = Modifier.testTag(ApprovalsTags.approve(account.uid)),
                    ) { Text(stringResource(R.string.admin_reactivate)) }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.admin_approvals_action_failed), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ApprovalsPreview() {
    OtliTheme {
        ApprovalsContent(
            state = ApprovalsUiState(
                isLoading = false,
                pending = listOf(UserAccount("m-new", Role.MERCHANT, AccountStatus.PENDING, "Nuevo Comercio", "nuevo@otli.test", "+50588880103")),
                active = listOf(UserAccount("m1", Role.MERCHANT, AccountStatus.ACTIVE, "Comedor Marta", "marta@otli.test", "+50588880101")),
                suspended = listOf(UserAccount("c1", Role.COURIER, AccountStatus.SUSPENDED, "Luis Mendoza", "luis@otli.test", "+50588880301")),
            ),
            onApprove = {},
            onSuspend = {},
            onDismissError = {},
        )
    }
}
