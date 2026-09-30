package com.otli.app.catalog.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otli.app.catalog.domain.Product

/** Container: wires [StorefrontViewModel]; [onAddToCart] is supplied by the cart in Slice 3. */
@Composable
fun StorefrontScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToCart: (Product) -> Unit = {},
    viewModel: StorefrontViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StorefrontContent(state = state, onAddToCart = onAddToCart, onBack = onBack, modifier = modifier)
}
