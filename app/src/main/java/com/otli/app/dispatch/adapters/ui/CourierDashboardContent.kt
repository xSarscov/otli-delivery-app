package com.otli.app.dispatch.adapters.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.otli.app.R
import com.otli.app.auth.adapters.ui.GateViewModel
import com.otli.app.auth.adapters.ui.SignOutMenuContent
import com.otli.app.core.ui.OtliTopBar

/**
 * Stateless courier home: the app bar with the sign-out menu, the availability switch, and one body
 * that the pool and the active delivery share. They never show together: the pool hides while the
 * courier has an active order, and the delivery renders nothing without one.
 */
@Composable
fun CourierDashboardContent(
    onSignOut: () -> Unit,
    availability: @Composable () -> Unit,
    pool: @Composable () -> Unit,
    activeDelivery: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        OtliTopBar(
            title = stringResource(R.string.app_name),
            actions = { SignOutMenuContent(onSignOut) },
        )
        availability()
        Box(Modifier.weight(1f)) {
            pool()
            activeDelivery()
        }
    }
}

/** Container: the courier home of the app, wired to the real screens and the real logout. */
@Composable
fun CourierDashboardScreen(
    modifier: Modifier = Modifier,
    session: GateViewModel = hiltViewModel(),
) {
    CourierDashboardContent(
        onSignOut = session::logout,
        availability = { AvailabilityScreen() },
        pool = { PoolScreen() },
        activeDelivery = { ActiveDeliveryScreen() },
        modifier = modifier,
    )
}
