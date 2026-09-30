package com.otli.app.auth.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Signs the current user out: from a session gate, or from the overflow menu of a role home. */
@HiltViewModel
class GateViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    fun logout() {
        viewModelScope.launch { repository.logout() }
    }
}
