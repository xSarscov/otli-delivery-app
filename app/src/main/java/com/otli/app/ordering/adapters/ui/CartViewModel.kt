package com.otli.app.ordering.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.catalog.domain.Product
import com.otli.app.core.money.Money
import com.otli.app.ordering.application.CartState
import com.otli.app.ordering.application.CartStore
import com.otli.app.ordering.application.PendingReplacement
import com.otli.app.ordering.domain.CartMerchant
import com.otli.app.ordering.domain.CheckoutCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CartLineUi(
    val productId: String,
    val name: String,
    val unitPrice: Money,
    val quantity: Int,
    val lineTotal: Money,
)

/** What the cart screen and the storefront's replace-cart dialog show. [pending] is the open merchant question. */
data class CartUiState(
    val merchantName: String? = null,
    val lines: List<CartLineUi> = emptyList(),
    val subtotal: Money = Money(0),
    val itemCount: Int = 0,
    val pending: PendingReplacement? = null,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

/** Presents the shared [CartStore]; every screen that touches the cart goes through here. */
@HiltViewModel
class CartViewModel @Inject constructor(private val store: CartStore) : ViewModel() {
    val uiState: StateFlow<CartUiState> = store.state
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, store.state.value.toUiState())

    fun add(merchant: CartMerchant, product: Product) = store.add(merchant, product)

    fun increase(productId: String) = change(productId, +1)

    fun decrease(productId: String) = change(productId, -1)

    fun remove(productId: String) = store.setQuantity(productId, 0)

    fun confirmReplacement() = store.confirmReplacement()

    fun dismissReplacement() = store.dismissReplacement()

    private fun change(productId: String, delta: Int) {
        val current = store.state.value.cart.lines.firstOrNull { it.productId == productId } ?: return
        store.setQuantity(productId, current.quantity + delta)
    }

    private fun CartState.toUiState() = CartUiState(
        merchantName = cart.merchant?.name,
        lines = cart.lines.map { CartLineUi(it.productId, it.name, it.unitPrice, it.quantity, it.unitPrice * it.quantity) },
        subtotal = CheckoutCalculator.totals(cart.lines, Money(0)).subtotal,
        itemCount = cart.itemCount,
        pending = pending,
    )
}
