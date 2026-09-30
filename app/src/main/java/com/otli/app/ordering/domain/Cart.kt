package com.otli.app.ordering.domain

import com.otli.app.catalog.domain.Product
import com.otli.app.core.money.Money

/** The merchant a cart is scoped to; the name is kept so the UI can ask "clear the cart from X?". */
data class CartMerchant(val id: String, val name: String)

/** One product in the cart with the unit price the customer saw when adding it. */
data class CartLine(val productId: String, val name: String, val unitPrice: Money, val quantity: Int)

sealed interface AddResult {
    data class Added(val cart: Cart) : AddResult

    /** The product belongs to another merchant than [current]; the cart was left untouched. */
    data class ConflictingMerchant(val current: CartMerchant) : AddResult
}

/**
 * A customer's cart: immutable, and scoped to a single merchant at a time.
 *
 * Adding a product of another merchant never changes the cart; it reports a
 * [AddResult.ConflictingMerchant] so the caller can ask the customer, then either keeps this cart
 * (declined) or calls [replaceWith] (confirmed).
 */
data class Cart(val merchant: CartMerchant? = null, val lines: List<CartLine> = emptyList()) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /** Total number of units across all lines. */
    val itemCount: Int get() = lines.sumOf { it.quantity }

    fun add(merchant: CartMerchant, product: Product, quantity: Int = 1): AddResult {
        require(quantity >= 1) { "Quantity must be at least 1: $quantity" }
        val current = this.merchant
        if (current != null && current.id != merchant.id) return AddResult.ConflictingMerchant(current)

        val existing = lines.firstOrNull { it.productId == product.id }
        val updated = if (existing == null) {
            lines + CartLine(product.id, product.name, product.price, quantity)
        } else {
            lines.map { if (it.productId == product.id) it.copy(quantity = it.quantity + quantity) else it }
        }
        return AddResult.Added(Cart(merchant, updated))
    }

    /** The confirmed path of a merchant conflict: drops the current lines and starts over with [product]. */
    fun replaceWith(merchant: CartMerchant, product: Product): Cart =
        Cart(merchant, listOf(CartLine(product.id, product.name, product.price, 1)))

    /** Sets a line's quantity; zero or less removes it, and an emptied cart is free of any merchant. */
    fun setQuantity(productId: String, quantity: Int): Cart {
        if (lines.none { it.productId == productId }) return this
        val updated = if (quantity <= 0) {
            lines.filterNot { it.productId == productId }
        } else {
            lines.map { if (it.productId == productId) it.copy(quantity = quantity) else it }
        }
        return if (updated.isEmpty()) EMPTY else Cart(merchant, updated)
    }

    fun clear(): Cart = EMPTY

    companion object {
        val EMPTY = Cart()
    }
}
