package com.otli.app.catalog.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otli.app.auth.adapters.ui.GateViewModel

/** Container: wires [MerchantListViewModel] and the sign-out action to the customer home. */
@Composable
fun MerchantListScreen(
    onMerchantClick: (String) -> Unit,
    onOpenOrders: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MerchantListViewModel = hiltViewModel(),
    session: GateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CustomerHomeContent(
        state = state,
        onMerchantClick = onMerchantClick,
        onOpenOrders = onOpenOrders,
        onSignOut = session::logout,
        modifier = modifier,
    )
}
