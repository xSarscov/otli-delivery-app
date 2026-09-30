package com.otli.app.auth.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Lets a user stuck behind a session gate (pending or suspended) sign out. */
@HiltViewModel
class GateViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    fun logout() {
        viewModelScope.launch { repository.logout() }
    }
}
