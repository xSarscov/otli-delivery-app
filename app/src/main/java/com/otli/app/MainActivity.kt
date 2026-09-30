package com.otli.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.otli.app.auth.adapters.ui.PendingApprovalScreen
import com.otli.app.auth.adapters.ui.SessionViewModel
import com.otli.app.auth.adapters.ui.SignedOutScreen
import com.otli.app.auth.adapters.ui.SuspendedScreen
import com.otli.app.catalog.adapters.ui.LocalPhotoLoader
import com.otli.app.catalog.adapters.ui.MerchantHomeTabsScreen
import com.otli.app.catalog.adapters.ui.PhotoLoader
import com.otli.app.core.navigation.RootNavHost
import com.otli.app.core.theme.OtliTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Single host activity: the session gate decides which graph is on screen. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var photoLoader: PhotoLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bars stay transparent with icons that follow the system theme; RootNavHost applies the insets.
        enableEdgeToEdge()
        setContent {
            OtliTheme {
                CompositionLocalProvider(LocalPhotoLoader provides photoLoader) {
                    val sessionViewModel: SessionViewModel = hiltViewModel()
                    RootNavHost(
                        session = sessionViewModel.session,
                        signedOut = { SignedOutScreen() },
                        pending = { role -> PendingApprovalScreen(role) },
                        suspended = { role -> SuspendedScreen(role) },
                        merchantHome = { MerchantHomeTabsScreen() },
                    )
                }
            }
        }
    }
}
