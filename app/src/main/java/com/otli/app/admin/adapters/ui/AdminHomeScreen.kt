package com.otli.app.admin.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.otli.app.auth.adapters.ui.GateViewModel

/** Container: keeps the selected tab across configuration changes (the orders needing attention come first) and hosts the Hilt screens. */
@Composable
fun AdminHomeTabsScreen(
    onOpenOrder: (String) -> Unit,
    modifier: Modifier = Modifier,
    session: GateViewModel = hiltViewModel(),
) {
    var selected by rememberSaveable { mutableStateOf(AdminTab.ACTIVE) }
    AdminHomeContent(
        selected = selected,
        onSelect = { selected = it },
        onSignOut = session::logout,
        active = { StuckOrdersScreen() },
        orders = { OrderListScreen(onOrderClick = onOpenOrder) },
        accounts = { ApprovalsScreen() },
        fee = { FeeSettingsScreen() },
        modifier = modifier,
    )
}
