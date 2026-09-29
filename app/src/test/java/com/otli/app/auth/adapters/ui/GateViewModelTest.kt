package com.otli.app.auth.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test

class GateViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repo = RecordingAuthRepository()

    @Test
    fun logoutSignsTheUserOutThroughTheRepository() {
        val viewModel = GateViewModel(repo)

        viewModel.logout()

        assertThat(repo.logoutCount).isEqualTo(1)
    }

    @Test
    fun eachLogoutRequestReachesTheRepository() {
        val viewModel = GateViewModel(repo)

        viewModel.logout()
        viewModel.logout()

        assertThat(repo.logoutCount).isEqualTo(2)
    }
}
