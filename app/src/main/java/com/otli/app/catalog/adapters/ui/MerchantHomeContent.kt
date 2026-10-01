package com.otli.app.catalog.adapters.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.otli.app.R
import com.otli.app.auth.adapters.ui.GateViewModel
import com.otli.app.auth.adapters.ui.SignOutMenuContent
import com.otli.app.core.ui.OtliTopBar

enum class MerchantTab { ORDERS, CATALOG, PROFILE }

/** Stateless merchant home: three tabs, each hosting one of the screens passed in as a slot. */
@Composable
fun MerchantHomeContent(
    selected: MerchantTab,
    onSelect: (MerchantTab) -> Unit,
    onSignOut: () -> Unit,
    orders: @Composable () -> Unit,
    catalog: @Composable () -> Unit,
    profile: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        OtliTopBar(
            title = stringResource(R.string.app_name),
            actions = { SignOutMenuContent(onSignOut) },
        )
        TabRow(selectedTabIndex = selected.ordinal) {
            Tab(
                selected = selected == MerchantTab.ORDERS,
                onClick = { onSelect(MerchantTab.ORDERS) },
                text = { Text(stringResource(R.string.tab_orders)) },
            )
            Tab(
                selected = selected == MerchantTab.CATALOG,
                onClick = { onSelect(MerchantTab.CATALOG) },
                text = { Text(stringResource(R.string.tab_catalog)) },
            )
            Tab(
                selected = selected == MerchantTab.PROFILE,
                onClick = { onSelect(MerchantTab.PROFILE) },
                text = { Text(stringResource(R.string.tab_profile)) },
            )
        }
        Box(Modifier.weight(1f)) {
            when (selected) {
                MerchantTab.ORDERS -> orders()
                MerchantTab.CATALOG -> catalog()
                MerchantTab.PROFILE -> profile()
            }
        }
    }
}

/** Container: keeps the selected tab across configuration changes (the order board comes first) and hosts the Hilt screens. */
@Composable
fun MerchantHomeTabsScreen(
    orders: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    session: GateViewModel = hiltViewModel(),
) {
    var selected by rememberSaveable { mutableStateOf(MerchantTab.ORDERS) }
    MerchantHomeContent(
        selected = selected,
        onSelect = { selected = it },
        onSignOut = session::logout,
        orders = orders,
        catalog = { MerchantCatalogScreen() },
        profile = { MerchantProfileScreen() },
        modifier = modifier,
    )
}
