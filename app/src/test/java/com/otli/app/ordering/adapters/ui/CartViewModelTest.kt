package com.otli.app.ordering.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.catalog.domain.Product
import com.otli.app.core.money.Money
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.ordering.application.CartStore
import com.otli.app.ordering.application.PendingReplacement
import com.otli.app.ordering.domain.CartMerchant
import org.junit.Rule
import org.junit.Test

class CartViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val store = CartStore()

    // Built on first use, after MainDispatcherRule has replaced Dispatchers.Main.
    private val viewModel by lazy { CartViewModel(store) }
    private val marta = CartMerchant("m1", "Comedor Marta")
    private val sol = CartMerchant("m2", "Pulperia Sol")
    private val nacatamal = product("p1", "Nacatamal", 12000)
    private val fresco = product("p2", "Fresco", 2500)

    private fun product(id: String, name: String, cents: Long) =
        Product(id, "c1", name, "", Money(cents), isAvailable = true, photoVersion = 0)

    private val state get() = viewModel.uiState.value

    @Test
    fun anEmptyCartShowsNoMerchantNoLinesAndAZeroSubtotal() {
        assertThat(state.isEmpty).isTrue()
        assertThat(state.merchantName).isNull()
        assertThat(state.subtotal).isEqualTo(Money(0))
        assertThat(state.itemCount).isEqualTo(0)
    }

    @Test
    fun addingFromAStorefrontShowsTheStoreLinesLineTotalsAndSubtotal() {
        viewModel.add(marta, nacatamal)
        viewModel.add(marta, nacatamal)
        viewModel.add(marta, fresco)

        assertThat(state.isEmpty).isFalse()
        assertThat(state.merchantName).isEqualTo("Comedor Marta")
        assertThat(state.lines).containsExactly(
            CartLineUi("p1", "Nacatamal", Money(12000), 2, Money(24000)),
            CartLineUi("p2", "Fresco", Money(2500), 1, Money(2500)),
        ).inOrder()
        assertThat(state.subtotal).isEqualTo(Money(26500))
        assertThat(state.itemCount).isEqualTo(3)
    }

    @Test
    fun increaseAndDecreaseStepAQuantityByOne() {
        viewModel.add(marta, nacatamal)

        viewModel.increase("p1")
        viewModel.increase("p1")
        assertThat(state.lines.single().quantity).isEqualTo(3)

        viewModel.decrease("p1")
        assertThat(state.lines.single().quantity).isEqualTo(2)
        assertThat(state.subtotal).isEqualTo(Money(24000))
    }

    @Test
    fun steppingAProductThatIsNoLongerInTheCartChangesNothing() {
        viewModel.add(marta, nacatamal)

        viewModel.increase("gone")
        viewModel.decrease("gone")

        assertThat(state.lines.map { it.productId to it.quantity }).containsExactly("p1" to 1)
    }

    @Test
    fun decreasingTheLastUnitRemovesTheLineAndAnEmptiedCartHasNoMerchant() {
        viewModel.add(marta, nacatamal)

        viewModel.decrease("p1")

        assertThat(state.isEmpty).isTrue()
        assertThat(state.merchantName).isNull()
    }

    @Test
    fun removeDropsTheWholeLineWhateverItsQuantity() {
        viewModel.add(marta, nacatamal)
        viewModel.add(marta, nacatamal)
        viewModel.add(marta, fresco)

        viewModel.remove("p1")

        assertThat(state.lines.map { it.productId }).containsExactly("p2")
    }

    @Test
    fun anotherMerchantsProductRaisesTheQuestionNamingTheCurrentStoreAndLeavesTheCart() {
        viewModel.add(marta, nacatamal)

        viewModel.add(sol, fresco)

        assertThat(state.pending).isEqualTo(PendingReplacement(current = marta, merchant = sol, product = fresco))
        assertThat(state.merchantName).isEqualTo("Comedor Marta")
        assertThat(state.lines.map { it.productId }).containsExactly("p1")
    }

    @Test
    fun confirmingTheQuestionReplacesTheCartAndDecliningKeepsIt() {
        viewModel.add(marta, nacatamal)
        viewModel.add(sol, fresco)
        viewModel.dismissReplacement()
        assertThat(state.pending).isNull()
        assertThat(state.merchantName).isEqualTo("Comedor Marta")

        viewModel.add(sol, fresco)
        viewModel.confirmReplacement()

        assertThat(state.pending).isNull()
        assertThat(state.merchantName).isEqualTo("Pulperia Sol")
        assertThat(state.lines.map { it.productId }).containsExactly("p2")
    }

    @Test
    fun aCartChangedElsewhereIsReflectedLive() {
        store.add(marta, nacatamal)
        assertThat(state.itemCount).isEqualTo(1)

        store.clear()

        assertThat(state.isEmpty).isTrue()
    }
}
