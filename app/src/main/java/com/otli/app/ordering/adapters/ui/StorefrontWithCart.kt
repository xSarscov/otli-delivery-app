package com.otli.app.ordering.adapters.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.otli.app.catalog.adapters.ui.StorefrontScreen
import com.otli.app.ordering.domain.CartMerchant

/**
 * The customer's storefront with the cart attached: adding puts the product in the shared cart
 * (asking first when it belongs to another store) and the top bar shows the cart button.
 */
@Composable
fun StorefrontWithCart(
    onBack: () -> Unit,
    onOpenCart: () -> Unit,
    modifier: Modifier = Modifier,
    cart: CartViewModel = hiltViewModel(),
) {
    val cartState by cart.uiState.collectAsStateWithLifecycle()
    StorefrontScreen(
        onBack = onBack,
        modifier = modifier,
        onAddToCart = { merchant, product -> cart.add(CartMerchant(merchant.id, merchant.name), product) },
        actions = { CartAction(itemCount = cartState.itemCount, onClick = onOpenCart) },
    )
    cartState.pending?.let { ReplaceCartDialog(it, onConfirm = cart::confirmReplacement, onDismiss = cart::dismissReplacement) }
}
