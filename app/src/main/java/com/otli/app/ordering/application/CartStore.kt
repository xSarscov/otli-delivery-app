package com.otli.app.ordering.application

import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.SessionState
import com.otli.app.catalog.domain.Product
import com.otli.app.ordering.domain.AddResult
import com.otli.app.ordering.domain.Cart
import com.otli.app.ordering.domain.CartMerchant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A product of another merchant waiting for the customer to decide whether to drop [current]'s cart. */
data class PendingReplacement(val current: CartMerchant, val merchant: CartMerchant, val product: Product)

data class CartState(val cart: Cart = Cart.EMPTY, val pending: PendingReplacement? = null)

/**
 * The customer's cart for as long as the app runs, shared by the storefront, the cart screen and
 * checkout so it survives browsing between stores. It is in memory only (MVP: no offline cart) and
 * is emptied when the session ends or belongs to someone who is not a customer.
 */
@Singleton
class CartStore @Inject constructor() {
    private val _state = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = _state.asStateFlow()

    /** Adds one unit; another merchant's product leaves the cart as is and becomes [CartState.pending]. */
    fun add(merchant: CartMerchant, product: Product) = _state.update { current ->
        when (val result = current.cart.add(merchant, product)) {
            is AddResult.Added -> CartState(result.cart)
            is AddResult.ConflictingMerchant -> current.copy(pending = PendingReplacement(result.current, merchant, product))
        }
    }

    /** The confirmed path of a merchant conflict: drops the current lines and starts over with the waiting product. */
    fun confirmReplacement() = _state.update { current ->
        val pending = current.pending ?: return@update current
        CartState(current.cart.replaceWith(pending.merchant, pending.product))
    }

    /** The declined path: the cart stays as it was. */
    fun dismissReplacement() = _state.update { it.copy(pending = null) }

    fun setQuantity(productId: String, quantity: Int) = _state.update { it.copy(cart = it.cart.setQuantity(productId, quantity)) }

    fun clear() = _state.update { CartState() }

    /** Keeps the cart only for an active customer (or while the session is still resolving). */
    fun onSessionChanged(session: SessionState) {
        val keeps = session == SessionState.Loading || session == SessionState.Active(Role.CUSTOMER)
        if (!keeps) clear()
    }
}
