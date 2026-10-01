package com.otli.app.catalog.adapters.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otli.app.catalog.domain.Merchant
import com.otli.app.catalog.domain.Product

/**
 * Container: wires [StorefrontViewModel]. The cart lives in another feature, so adding a product
 * is reported with the store it belongs to, and the top bar takes extra [actions] (the cart button).
 */
@Composable
fun StorefrontScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToCart: (Merchant, Product) -> Unit = { _, _ -> },
    actions: @Composable RowScope.() -> Unit = {},
    viewModel: StorefrontViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StorefrontContent(
        state = state,
        onAddToCart = { product -> state.merchant?.let { onAddToCart(it, product) } },
        onBack = onBack,
        modifier = modifier,
        actions = actions,
    )
}
