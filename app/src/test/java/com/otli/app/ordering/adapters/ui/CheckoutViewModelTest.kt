package com.otli.app.ordering.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.application.AuthRepository
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.AuthUser
import com.otli.app.auth.domain.MerchantStoreDetails
import com.otli.app.auth.domain.ProfileFields
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import com.otli.app.catalog.adapters.ui.FakeCatalogRepository
import com.otli.app.catalog.adapters.ui.aCategory
import com.otli.app.catalog.adapters.ui.aMerchant
import com.otli.app.catalog.adapters.ui.aProduct
import com.otli.app.catalog.application.Storefront
import com.otli.app.core.map.MapPin
import com.otli.app.core.money.Money
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.ordering.application.CartStore
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.FakeSettingsRepository
import com.otli.app.ordering.application.PlaceOrder
import com.otli.app.ordering.application.PlaceOrderRejection
import com.otli.app.ordering.domain.CartMerchant
import com.otli.app.ordering.domain.OrderLocation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

/** A signed-in customer; [account] is what `users/{uid}` holds (null = profile document missing). */
private class SignedInCustomer(var account: UserAccount? = UserAccount("customer-1", Role.CUSTOMER, AccountStatus.ACTIVE, "Ana Lopez", "a@b.c", "+50588880201")) :
    AuthRepository {
    override fun observeAuthState(): Flow<AuthUser?> = flowOf(AuthUser("customer-1", "a@b.c"))

    override fun observeUserDocument(uid: String): Flow<UserAccount?> = flowOf(account)

    override suspend fun register(email: String, password: String, role: Role, profileFields: ProfileFields, merchantStore: MerchantStoreDetails?) =
        Result.success(Unit)

    override suspend fun login(email: String, password: String) = Result.success(Unit)

    override suspend fun logout() = Unit
}

class CheckoutViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val cart = CartStore()
    private val catalog = FakeCatalogRepository()
    private val settings = FakeSettingsRepository()
    private val orders = FakeOrderRepository()
    private val auth = SignedInCustomer()
    private val marta = CartMerchant("m1", "Comedor Marta")
    private val nacatamal = aProduct("p1", "c1", "Nacatamal", 12000)
    private val fresco = aProduct("p2", "c2", "Fresco", 2500)

    init {
        catalog.storefront = MutableStateFlow(
            Storefront(aMerchant("m1", "Comedor Marta"), listOf(aCategory("c1"), aCategory("c2")), listOf(nacatamal, fresco)),
        )
        cart.add(marta, nacatamal)
        cart.add(marta, nacatamal)
        cart.add(marta, fresco)
    }

    // Built on first use, after MainDispatcherRule has replaced Dispatchers.Main.
    private fun viewModel() = CheckoutViewModel(cart, PlaceOrder(catalog, settings, orders), settings, auth)

    private fun CheckoutViewModel.fillForm(pin: MapPin = MapPin(12.27, -86.57), reference: String = "Casa azul", cash: Boolean = true) {
        setPin(pin)
        setReference(reference)
        setCashConfirmed(cash)
    }

    @Test
    fun showsTheStoreTheSubtotalTheLiveFeeAndTheTotal() {
        val state = viewModel().uiState.value

        assertThat(state.merchantName).isEqualTo("Comedor Marta")
        assertThat(state.subtotal).isEqualTo(Money(26500))
        assertThat(state.fee).isEqualTo(Money(3000))
        assertThat(state.total).isEqualTo(Money(29500))
        assertThat(state.isCartEmpty).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun aFeeThatCannotBeReadLeavesNoTotalAndSaysSo() {
        settings.fee = Result.failure(IllegalStateException("offline"))

        val state = viewModel().uiState.value

        assertThat(state.fee).isNull()
        assertThat(state.total).isNull()
        assertThat(state.error).isEqualTo(CheckoutError.Rejected(PlaceOrderRejection.FeeUnavailable))
    }

    @Test
    fun placingWithoutAPinIsBlockedAndNothingIsWritten() {
        val viewModel = viewModel()
        viewModel.setReference("Casa azul")
        viewModel.setCashConfirmed(true)

        viewModel.place()

        assertThat(viewModel.uiState.value.error).isEqualTo(CheckoutError.PinRequired)
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun aCompleteFormPlacesTheOrderWithPinReferenceCustomerAndTotalsThenEmptiesTheCart() {
        val viewModel = viewModel()
        viewModel.fillForm(pin = MapPin(12.28, -86.58), reference = "  Casa azul frente a la pulperia ")

        viewModel.place()

        val draft = orders.placed.single()
        assertThat(draft.dropoff).isEqualTo(OrderLocation(12.28, -86.58, "Casa azul frente a la pulperia"))
        assertThat(draft.customerId).isEqualTo("customer-1")
        assertThat(draft.customerName).isEqualTo("Ana Lopez")
        assertThat(draft.customerPhone).isEqualTo("+50588880201")
        assertThat(draft.totals.total).isEqualTo(Money(29500))
        assertThat(viewModel.uiState.value.placedOrderId).isEqualTo("order-1")
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.isPlacing).isFalse()
        assertThat(cart.state.value.cart.isEmpty).isTrue()
    }

    @Test
    fun aFeeChangedSinceTheScreenOpenedShowsTheNewTotalAndNeedsASecondConfirmation() {
        val viewModel = viewModel()
        viewModel.fillForm()
        settings.fee = Result.success(Money(4500))

        viewModel.place()

        assertThat(viewModel.uiState.value.error).isEqualTo(CheckoutError.FeeChanged(Money(4500)))
        assertThat(viewModel.uiState.value.fee).isEqualTo(Money(4500))
        assertThat(viewModel.uiState.value.total).isEqualTo(Money(31000))
        assertThat(orders.placed).isEmpty()

        viewModel.place()

        assertThat(orders.placed.single().totals.fee).isEqualTo(Money(4500))
        assertThat(viewModel.uiState.value.placedOrderId).isEqualTo("order-1")
    }

    @Test
    fun aStoreThatClosedRejectsWithItsNameAndKeepsTheCart() {
        catalog.storefront = MutableStateFlow(
            Storefront(aMerchant("m1", "Comedor Marta", isOpen = false), listOf(aCategory("c1"), aCategory("c2")), listOf(nacatamal, fresco)),
        )
        val viewModel = viewModel()
        viewModel.fillForm()

        viewModel.place()

        assertThat(viewModel.uiState.value.error).isEqualTo(CheckoutError.Rejected(PlaceOrderRejection.StoreClosed("Comedor Marta")))
        assertThat(viewModel.uiState.value.placedOrderId).isNull()
        assertThat(cart.state.value.cart.isEmpty).isFalse()
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun anUnavailableProductRejectsNamingTheItem() {
        catalog.storefront = MutableStateFlow(
            Storefront(
                aMerchant("m1", "Comedor Marta"),
                listOf(aCategory("c1"), aCategory("c2")),
                listOf(nacatamal, aProduct("p2", "c2", "Fresco", 2500, isAvailable = false)),
            ),
        )
        val viewModel = viewModel()
        viewModel.fillForm()

        viewModel.place()

        assertThat(viewModel.uiState.value.error).isEqualTo(CheckoutError.Rejected(PlaceOrderRejection.ItemsUnavailable(listOf("Fresco"))))
    }

    @Test
    fun aFailedWriteCanBeRetriedWithTheFormIntact() {
        orders.outcome = { Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()
        viewModel.fillForm(reference = "Casa azul")

        viewModel.place()

        assertThat(viewModel.uiState.value.error).isEqualTo(CheckoutError.Rejected(PlaceOrderRejection.PlacementFailed))
        assertThat(viewModel.uiState.value.isPlacing).isFalse()
        assertThat(viewModel.uiState.value.reference).isEqualTo("Casa azul")

        orders.outcome = { Result.success("order-ok") }
        viewModel.place()

        assertThat(viewModel.uiState.value.placedOrderId).isEqualTo("order-ok")
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun aCartEmptiedBeforeAnyFormInputReportsTheEmptyCartRatherThanAMissingPin() {
        val viewModel = viewModel()
        cart.clear()

        viewModel.place()

        assertThat(viewModel.uiState.value.error).isEqualTo(CheckoutError.Rejected(PlaceOrderRejection.EmptyCart))
    }

    @Test
    fun aFeeThatWasUnreadableWhenTheScreenOpenedIsShownBeforeTheOrderIsPlaced() {
        settings.fee = Result.failure(IllegalStateException("offline"))
        val viewModel = viewModel()
        viewModel.fillForm()
        settings.fee = Result.success(Money(3000))

        viewModel.place()

        assertThat(viewModel.uiState.value.error).isEqualTo(CheckoutError.FeeChanged(Money(3000)))
        assertThat(viewModel.uiState.value.total).isEqualTo(Money(29500))
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun aMissingProfileStopsBeforeAnythingIsWritten() {
        auth.account = null
        val viewModel = viewModel()
        viewModel.fillForm()

        viewModel.place()

        assertThat(viewModel.uiState.value.error).isEqualTo(CheckoutError.ProfileUnavailable)
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun anEmptiedCartCannotBePlaced() {
        val viewModel = viewModel()
        viewModel.fillForm()
        cart.clear()

        viewModel.place()

        assertThat(viewModel.uiState.value.isCartEmpty).isTrue()
        assertThat(viewModel.uiState.value.error).isEqualTo(CheckoutError.Rejected(PlaceOrderRejection.EmptyCart))
        assertThat(orders.placed).isEmpty()
    }

    @Test
    fun editingTheFormClearsTheMessageAboutAPreviousAttempt() {
        val viewModel = viewModel()
        viewModel.place()
        assertThat(viewModel.uiState.value.error).isEqualTo(CheckoutError.PinRequired)

        viewModel.setPin(MapPin(12.27, -86.57))

        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.pin).isEqualTo(MapPin(12.27, -86.57))
    }

    @Test
    fun aSecondTapWhilePlacingDoesNotPlaceTwice() {
        val viewModel = viewModel()
        viewModel.fillForm()
        val gate = CompletableDeferred<Unit>()
        settings.beforeRead = { gate.await() }

        viewModel.place()
        assertThat(viewModel.uiState.value.isPlacing).isTrue()
        viewModel.place()
        gate.complete(Unit)

        assertThat(orders.placed).hasSize(1)
    }
}
