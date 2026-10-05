package com.otli.app.admin.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.admin.application.AdminActionResult
import com.otli.app.admin.application.AdminRepository
import com.otli.app.admin.application.ApproveAccount
import com.otli.app.admin.application.SuspendAccount
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.UserAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ApprovalError { ACTION_FAILED }

data class ApprovalsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** Merchants and couriers waiting for Admin: the approval queue. */
    val pending: List<UserAccount> = emptyList(),
    val active: List<UserAccount> = emptyList(),
    val suspended: List<UserAccount> = emptyList(),
    /** The account an action is running for; nothing else can be acted on meanwhile. */
    val busyUid: String? = null,
    val error: ApprovalError? = null,
)

/** Admin's list of merchant and courier accounts by status, with approve, suspend and reactivate. */
@HiltViewModel
class ApprovalsViewModel @Inject constructor(
    admin: AdminRepository,
    private val approveAccount: ApproveAccount,
    private val suspendAccount: SuspendAccount,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ApprovalsUiState())
    val uiState: StateFlow<ApprovalsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            admin.observeManagedAccounts()
                // A rejected listener (e.g. the rules deny reads after sign-out) must never crash the app.
                .catch { _uiState.update { it.copy(isLoading = false, loadFailed = true) } }
                .collect { accounts ->
                    val byName = accounts.sortedWith(compareBy<UserAccount>({ it.displayName.lowercase() }, { it.uid }))
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadFailed = false,
                            pending = byName.filter { account -> account.status == AccountStatus.PENDING },
                            active = byName.filter { account -> account.status == AccountStatus.ACTIVE },
                            suspended = byName.filter { account -> account.status == AccountStatus.SUSPENDED },
                        )
                    }
                }
        }
    }

    /** Approves a pending account or reactivates a suspended one. */
    fun approve(account: UserAccount) = run(account) { approveAccount(account) }

    fun suspend(account: UserAccount) = run(account) { suspendAccount(account) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    private fun run(account: UserAccount, action: suspend () -> AdminActionResult) {
        if (_uiState.value.busyUid != null) return
        _uiState.update { it.copy(busyUid = account.uid, error = null) }
        viewModelScope.launch {
            val outcome = action()
            _uiState.update {
                it.copy(busyUid = null, error = if (outcome == AdminActionResult.Done) null else ApprovalError.ACTION_FAILED)
            }
        }
    }
}
