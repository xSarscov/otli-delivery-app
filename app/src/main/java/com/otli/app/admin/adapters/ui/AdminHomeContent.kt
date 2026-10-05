package com.otli.app.admin.adapters.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.otli.app.R
import com.otli.app.auth.adapters.ui.SignOutMenuContent
import com.otli.app.core.ui.OtliTopBar

enum class AdminTab(val title: Int) {
    ACTIVE(R.string.admin_tab_active),
    ORDERS(R.string.admin_tab_orders),
    ACCOUNTS(R.string.admin_tab_accounts),
    FEE(R.string.admin_tab_fee),
}

/** Stateless Admin home: four tabs, each hosting one of the screens passed in as a slot. */
@Composable
fun AdminHomeContent(
    selected: AdminTab,
    onSelect: (AdminTab) -> Unit,
    onSignOut: () -> Unit,
    active: @Composable () -> Unit,
    orders: @Composable () -> Unit,
    accounts: @Composable () -> Unit,
    fee: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        OtliTopBar(title = stringResource(R.string.app_name), actions = { SignOutMenuContent(onSignOut) })
        ScrollableTabRow(selectedTabIndex = selected.ordinal, edgePadding = 0.dp) {
            for (tab in AdminTab.entries) {
                Tab(selected = selected == tab, onClick = { onSelect(tab) }, text = { Text(stringResource(tab.title)) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (selected) {
                AdminTab.ACTIVE -> active()
                AdminTab.ORDERS -> orders()
                AdminTab.ACCOUNTS -> accounts()
                AdminTab.FEE -> fee()
            }
        }
    }
}
