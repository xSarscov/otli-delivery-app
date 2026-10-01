package com.otli.app.catalog.adapters.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.otli.app.R
import com.otli.app.auth.adapters.ui.SignOutMenuContent
import com.otli.app.core.ui.OtliTopBar

/** Stateless customer home: the merchant list under an app bar that offers My orders and Sign out. */
@Composable
fun CustomerHomeContent(
    state: MerchantListUiState,
    onMerchantClick: (String) -> Unit,
    onOpenOrders: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        OtliTopBar(
            title = stringResource(R.string.app_name),
            actions = {
                TextButton(onClick = onOpenOrders) { Text(stringResource(R.string.my_orders_action)) }
                SignOutMenuContent(onSignOut)
            },
        )
        MerchantListContent(state = state, onMerchantClick = onMerchantClick, modifier = Modifier.weight(1f))
    }
}
