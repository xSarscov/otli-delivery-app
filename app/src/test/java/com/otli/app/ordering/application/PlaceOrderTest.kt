package com.otli.app.ordering.application

import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.catalog.adapters.ui.FakeCatalogRepository
import com.otli.app.catalog.adapters.ui.aCategory
import com.otli.app.catalog.adapters.ui.aMerchant
import com.otli.app.catalog.adapters.ui.aProduct
import com.otli.app.catalog.application.Storefront
import com.otli.app.catalog.domain.MerchantLocation
import com.otli.app.catalog.domain.Product
import com.otli.app.core.money.Money
import com.otli.app.ordering.domain.Cart
import com.otli.app.ordering.domain.CartLine
import com.otli.app.ordering.domain.CartMerchant
import com.otli.app.ordering.domain.OrderDraft
import com.otli.app.ordering.domain.OrderItem
import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.Totals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaceOrderTest {
    private val customer = OrderCustomer(id = "customer-1", name = "Ana Lopez", phone = "+50588882222")
    private val dropoff = OrderLocation(12.27, -86.57, "Casa azul frente a la pulperia")
    private val catalog = FakeCatalogRepository()
    private val orders = FakeOrderRepository()
    private val settings = FakeSettingsRepository()
    private val placeOrder = PlaceOrder(catalog, settings, orders)

    private val nacatamal = aProduct("p1", "c1", "Nacatamal", 12000)
    private val fresco = aProduct("p2", "c2", "Fresco", 2500)

    private fun showStore(
        name: String = "Comedor Marta",
        isOpen: Boolean = true,
        products: List<Product> = listOf(nacatamal, fresco),
    ) {
        catalog.storefront = MutableStateFlow(
            Storefront(aMerchant("m1", name, isOpen = isOpen), listOf(aCategory("c1"), aCategory("c2")), products),
        )
    }

    private fun cartOf(vararg lines: CartLine, name: String = "Comedor Marta") = Cart(CartMerchant("m1", name), lines.toList())

    private val standardCart = cartOf(CartLine("p1", "Nacatamal", Money(12000), 2), CartLine("p2", "Fresco", Money(2500), 1))

    private suspend fun place(cart: Cart = standardCart) = placeOrder(customer, cart, dropoff)

    @Test
    fun aValidOrderIsPlacedWithAFullSnapshotAndReturnsItsId() = runTest {
        showStore()

        val result = place()

        assertThat(result).isEqualTo(PlaceOrderResult.Placed("order-1"))
        assertThat(orders.placed).containsExactly(
            OrderDraft(
                customerId = "customer-1",
                customerName = "Ana Lopez",
                customerPhone = "+50588882222",
                merchantId = "m1",
                merchantName = "Comedor Marta",
                pickup = OrderLocation(12.2667, -86.5667, ""),
                dropoff = dropoff,
                items = listOf(OrderItem("p1", "Nacatamal", Money(12000), 2), OrderItem("p2", "Fresco", Money(2500), 1)),
                totals = Totals(subtotal = Money(26500), fee = Money(3000), total = Money(29500)),
            ),
        )
    }

    @Test
    fun theFeeIsTheOneConfiguredAtPlacementTime() = runTest {
        showStore()
        place()

        settings.fee = Result.success(Money(4500))
        place()

        assertThat(orders.placed.map { it.totals.fee }).containsExactly(Money(3000), Money(4500)).inOrder()
        assertThat(orders.placed.map { it.totals.total }).containsExactly(Money(29500), Money(31000)).inOrder()
        assertThat(orders.placed.map { it.totals.subtotal }.distinct()).containsExactly(Money(26500))
    }

    @Test
    fun aClosedStoreBlocksPlacementAndNamesTheMerchant() = runTest {
        showStore(isOpen = false)

        val result = place()

        assertThat(result).isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.StoreClosed("Comedor Marta")))
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun theClosedStoreMessageNamesWhicheverMerchantClosed() = runTest {
        showStore(name = "Pulperia Sol", isOpen = false)

        val result = place(cartOf(CartLine("p1", "Nacatamal", Money(12000), 1), name = "Pulperia Sol"))

        assertThat(result).isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.StoreClosed("Pulperia Sol")))
    }

    @Test
    fun aClosedStoreIsReportedBeforeUnavailableItems() = runTest {
        showStore(isOpen = false, products = listOf(nacatamal.copy(isAvailable = false), fresco))

        val result = place()

        assertThat(result).isInstanceOf(PlaceOrderResult.Rejected::class.java)
        assertThat((result as PlaceOrderResult.Rejected).reason).isInstanceOf(PlaceOrderRejection.StoreClosed::class.java)
    }

    @Test
    fun anItemThatBecameUnavailableBlocksPlacementAndIsNamed() = runTest {
        showStore(products = listOf(nacatamal, fresco.copy(isAvailable = false)))

        val result = place()

        assertThat(result).isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.ItemsUnavailable(listOf("Fresco"))))
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun everyUnavailableItemIsNamedAndAvailableOnesAreNot() = runTest {
        showStore(products = listOf(nacatamal.copy(isAvailable = false), fresco.copy(isAvailable = false)))

        val result = place()

        assertThat(result).isEqualTo(
            PlaceOrderResult.Rejected(PlaceOrderRejection.ItemsUnavailable(listOf("Nacatamal", "Fresco"))),
        )
    }

    @Test
    fun anItemRemovedFromTheCatalogCountsAsUnavailable() = runTest {
        showStore(products = listOf(nacatamal))

        val result = place()

        assertThat(result).isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.ItemsUnavailable(listOf("Fresco"))))
    }

    @Test
    fun aMissingMerchantBlocksPlacement() = runTest {
        catalog.storefront = MutableStateFlow(null)

        assertThat(place()).isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.StoreUnavailable("Comedor Marta")))
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun aMerchantThatIsNotActiveOrHasNoLocationBlocksPlacement() = runTest {
        val inactive = aMerchant("m1", isOpen = true).copy(status = AccountStatus.SUSPENDED)
        catalog.storefront = MutableStateFlow(Storefront(inactive, emptyList(), listOf(nacatamal, fresco)))
        assertThat(place()).isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.StoreUnavailable("Comedor Marta")))

        val noPin = aMerchant("m1", isOpen = true).copy(location = null)
        catalog.storefront = MutableStateFlow(Storefront(noPin, emptyList(), listOf(nacatamal, fresco)))
        assertThat(place()).isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.StoreUnavailable("Comedor Marta")))
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun theMerchantPinBecomesThePickupLocation() = runTest {
        val merchant = aMerchant("m1", isOpen = true).copy(location = MerchantLocation(12.3, -86.4, "Frente al parque"))
        catalog.storefront = MutableStateFlow(Storefront(merchant, emptyList(), listOf(nacatamal, fresco)))

        place()

        assertThat(orders.placed.single().pickup).isEqualTo(OrderLocation(12.3, -86.4, "Frente al parque"))
    }

    @Test
    fun anEmptyCartIsRejectedWithoutTouchingTheStoreOrTheFee() = runTest {
        showStore()

        val result = place(Cart.EMPTY)

        assertThat(result).isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.EmptyCart))
        assertThat(catalog.storefrontRequests).isEmpty()
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun aCartThatHasAMerchantButNoLinesIsRejectedAsEmpty() = runTest {
        showStore()

        val result = place(Cart(CartMerchant("m1", "Comedor Marta"), emptyList()))

        assertThat(result).isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.EmptyCart))
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun aCartWithMoreLinesThanAnOrderCanHoldIsRejected() = runTest {
        val lines = (1..31).map { CartLine("p$it", "Item $it", Money(100), 1) }
        showStore(products = lines.map { aProduct(it.productId, "c1", it.name, 100) })

        assertThat(place(cartOf(*lines.toTypedArray())))
            .isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.TooManyItems(max = 30)))
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun aCartWithExactlyTheMaximumNumberOfLinesIsPlaced() = runTest {
        val lines = (1..30).map { CartLine("p$it", "Item $it", Money(100), 1) }
        showStore(products = lines.map { aProduct(it.productId, "c1", it.name, 100) })

        assertThat(place(cartOf(*lines.toTypedArray()))).isInstanceOf(PlaceOrderResult.Placed::class.java)
        assertThat(orders.placed.single().items).hasSize(30)
    }

    @Test
    fun anUnreadableFeeBlocksPlacement() = runTest {
        showStore()
        settings.fee = Result.failure(IllegalStateException("offline"))

        assertThat(place()).isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.FeeUnavailable))
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun aFailedWriteIsReportedAsAFailedPlacement() = runTest {
        showStore()
        orders.outcome = { Result.failure(IllegalStateException("permission denied")) }

        assertThat(place()).isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.PlacementFailed))
        assertThat(orders.placed).hasSize(1)
    }

    @Test
    fun aStorefrontThatCannotBeReadIsReportedAsAFailedPlacement() = runTest {
        catalog.storefront = flow { error("permission denied") }

        assertThat(place()).isEqualTo(PlaceOrderResult.Rejected(PlaceOrderRejection.PlacementFailed))
        assertThat(orders.placed).isEmpty()
    }
}
