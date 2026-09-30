package com.otli.app.ordering.application

import com.otli.app.auth.domain.AccountStatus
import com.otli.app.catalog.application.CatalogRepository
import com.otli.app.catalog.domain.Merchant
import com.otli.app.core.money.Money
import com.otli.app.core.result.suspendRunCatching
import com.otli.app.ordering.domain.Cart
import com.otli.app.ordering.domain.CheckoutCalculator
import com.otli.app.ordering.domain.OrderDraft
import com.otli.app.ordering.domain.OrderItem
import com.otli.app.ordering.domain.OrderLocation
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Who is ordering, as recorded on the order. */
data class OrderCustomer(val id: String, val name: String, val phone: String)

sealed interface PlaceOrderResult {
    data class Placed(val orderId: String) : PlaceOrderResult

    data class Rejected(val reason: PlaceOrderRejection) : PlaceOrderResult
}

/** Why an order was not placed; nothing is written for any of these. */
sealed interface PlaceOrderRejection {
    data object EmptyCart : PlaceOrderRejection

    /** An order holds at most [max] different products (enforced by the rules as well). */
    data class TooManyItems(val max: Int) : PlaceOrderRejection

    data class StoreClosed(val merchantName: String) : PlaceOrderRejection

    /** The merchant is gone, not active, or has no pickup pin. */
    data class StoreUnavailable(val merchantName: String) : PlaceOrderRejection

    /** Names of the cart items that are unavailable or no longer in the catalog. */
    data class ItemsUnavailable(val names: List<String>) : PlaceOrderRejection

    data object FeeUnavailable : PlaceOrderRejection

    /** The store or the write could not be reached, or the rules refused the order. */
    data object PlacementFailed : PlaceOrderRejection
}

/**
 * Turns the cart into an order. It checks the live store and product state first, so a stale cart
 * is refused before anything is written, then builds the snapshot with the fee configured right now.
 * The rules re-check the store and the totals on write; this is the friendly early exit (ADR-10).
 */
class PlaceOrder @Inject constructor(
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository,
    private val orders: OrderRepository,
) {
    suspend operator fun invoke(customer: OrderCustomer, cart: Cart, dropoff: OrderLocation): PlaceOrderResult {
        val merchant = cart.merchant ?: return rejected(PlaceOrderRejection.EmptyCart)
        if (cart.isEmpty) return rejected(PlaceOrderRejection.EmptyCart)
        if (cart.lines.size > MAX_LINES) return rejected(PlaceOrderRejection.TooManyItems(MAX_LINES))

        val storefront = suspendRunCatching { catalog.observeStorefront(merchant.id).first() }
            .getOrElse { return rejected(PlaceOrderRejection.PlacementFailed) }
        val store = storefront?.merchant
        val pickup = store?.location
        if (store == null || pickup == null || store.status != AccountStatus.ACTIVE) {
            return rejected(PlaceOrderRejection.StoreUnavailable(merchant.name))
        }
        if (!store.isOpen) return rejected(PlaceOrderRejection.StoreClosed(store.name))

        val available = storefront.products.filter { it.isAvailable }.map { it.id }.toSet()
        val unavailable = cart.lines.filterNot { it.productId in available }
        if (unavailable.isNotEmpty()) return rejected(PlaceOrderRejection.ItemsUnavailable(unavailable.map { it.name }))

        val fee = settings.deliveryFee().getOrElse { return rejected(PlaceOrderRejection.FeeUnavailable) }
        val draft = draftOf(customer, store, OrderLocation(pickup.latitude, pickup.longitude, pickup.reference), dropoff, cart, fee)
        return orders.place(draft).fold(
            onSuccess = { PlaceOrderResult.Placed(it) },
            onFailure = { rejected(PlaceOrderRejection.PlacementFailed) },
        )
    }

    private fun draftOf(
        customer: OrderCustomer,
        store: Merchant,
        pickup: OrderLocation,
        dropoff: OrderLocation,
        cart: Cart,
        fee: Money,
    ) = OrderDraft(
        customerId = customer.id,
        customerName = customer.name,
        customerPhone = customer.phone,
        merchantId = store.id,
        merchantName = store.name,
        pickup = pickup,
        dropoff = dropoff,
        items = cart.lines.map { OrderItem(it.productId, it.name, it.unitPrice, it.quantity) },
        totals = CheckoutCalculator.totals(cart.lines, fee),
    )

    private fun rejected(reason: PlaceOrderRejection) = PlaceOrderResult.Rejected(reason)

    companion object {
        /** Matches the `items.size() <= 30` bound in the order creation rule. */
        const val MAX_LINES = 30
    }
}
