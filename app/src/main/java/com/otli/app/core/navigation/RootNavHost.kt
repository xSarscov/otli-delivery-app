package com.otli.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 */
@Composable
fun RootNavHost(
    session: StateFlow<SessionState>,
    modifier: Modifier = Modifier,
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

    NavHost(navController, startDestination = start, modifier = modifier) {
        composable<LoadingRoute> { LoadingScreen() }
        navigation<SignedOutGraph>(startDestination = SignedOutStart) {
            composable<SignedOutStart> { signedOut() }
        }
        composable<Gate.ProfileIncomplete> { profileIncomplete() }
        composable<Gate.PendingApproval> { entry -> pending(entry.toRoute<Gate.PendingApproval>().role) }
        composable<Gate.Suspended> { entry -> suspended(entry.toRoute<Gate.Suspended>().role) }
        navigation<CustomerGraph>(startDestination = CustomerHome) {
            composable<CustomerHome> { CustomerHomeScreen() }
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
