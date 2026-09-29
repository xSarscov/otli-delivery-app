package com.otli.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.otli.app.auth.adapters.ui.PendingApprovalScreen
import com.otli.app.auth.adapters.ui.RegisterScreen
import com.otli.app.auth.adapters.ui.SessionViewModel
import com.otli.app.auth.adapters.ui.SuspendedScreen
import com.otli.app.core.navigation.RootNavHost
import com.otli.app.core.theme.OtliTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single host activity: the session gate decides which graph is on screen. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OtliTheme {
                val sessionViewModel: SessionViewModel = hiltViewModel()
                RootNavHost(
                    session = sessionViewModel.session,
                    signedOut = { RegisterScreen() },
                    pending = { role -> PendingApprovalScreen(role) },
                    suspended = { role -> SuspendedScreen(role) },
                )
            }
        }
    }
}
