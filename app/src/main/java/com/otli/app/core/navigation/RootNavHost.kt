package com.otli.app.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.otli.app.R
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.SessionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Root of the app: one destination per [SessionState] (ADR-5).
 *
 * Every session change replaces the whole back stack, and the role graphs share no
 * routes, so no navigation path leads from one role's screens to another's.
 * The signed-out and gate slots default to placeholders until the auth forms provide them.
 *
 * The host is also the single owner of window chrome: an opaque themed [Surface] fills the whole
 * window (so content colors match [MaterialTheme] in dark mode too) and [windowInsets] keep every
 * screen clear of the system bars and the keyboard. Screens must not pad for bars themselves.
 */
@Composable
fun RootNavHost(
    session: StateFlow<SessionState>,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    signedOut: @Composable () -> Unit = { HomePlaceholder(R.string.signed_out_placeholder, RootTags.SIGNED_OUT) },
    profileIncomplete: @Composable () -> Unit = {
        HomePlaceholder(R.string.profile_incomplete_placeholder, RootTags.PROFILE_INCOMPLETE)
    },
    pending: @Composable (Role) -> Unit = { role ->
        HomePlaceholder(R.string.pending_placeholder, RootTags.pending(role))
    },
    suspended: @Composable (Role) -> Unit = { role ->
        HomePlaceholder(R.string.suspended_placeholder, RootTags.suspended(role))
    },
    customerHome: @Composable (onOpenMerchant: (String) -> Unit) -> Unit = { CustomerHomeScreen() },
    storefront: @Composable (merchantId: String) -> Unit = {
        HomePlaceholder(R.string.home_customer, RootTags.STOREFRONT)
    },
    merchantHome: @Composable () -> Unit = { MerchantHomeScreen() },
) {
    val state by session.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val target = routeFor(state)
    val start = remember { target }

    LaunchedEffect(target) {
        navController.navigate(target) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(windowInsets)) {
            RootGraph(navController, start, signedOut, profileIncomplete, pending, suspended, customerHome, storefront, merchantHome)
        }
    }
}

@Composable
private fun RootGraph(
    navController: NavHostController,
    start: Any,
    signedOut: @Composable () -> Unit,
    profileIncomplete: @Composable () -> Unit,
    pending: @Composable (Role) -> Unit,
    suspended: @Composable (Role) -> Unit,
    customerHome: @Composable (onOpenMerchant: (String) -> Unit) -> Unit,
    storefront: @Composable (merchantId: String) -> Unit,
    merchantHome: @Composable () -> Unit,
) {
    NavHost(navController, startDestination = start) {
        composable<LoadingRoute> { LoadingScreen() }
        navigation<SignedOutGraph>(startDestination = SignedOutStart) {
            composable<SignedOutStart> { signedOut() }
        }
        composable<Gate.ProfileIncomplete> { profileIncomplete() }
        composable<Gate.PendingApproval> { entry -> pending(entry.toRoute<Gate.PendingApproval>().role) }
        composable<Gate.Suspended> { entry -> suspended(entry.toRoute<Gate.Suspended>().role) }
        navigation<CustomerGraph>(startDestination = CustomerHome) {
            composable<CustomerHome> {
                customerHome { merchantId -> navController.navigate(StorefrontRoute(merchantId)) }
            }
            composable<StorefrontRoute> { entry -> storefront(entry.toRoute<StorefrontRoute>().merchantId) }
        }
        navigation<MerchantGraph>(startDestination = MerchantHome) {
            composable<MerchantHome> { merchantHome() }
        }
        navigation<CourierGraph>(startDestination = CourierHome) {
            composable<CourierHome> { CourierHomeScreen() }
        }
        navigation<AdminGraph>(startDestination = AdminHome) {
            composable<AdminHome> { AdminHomeScreen() }
        }
    }
}
