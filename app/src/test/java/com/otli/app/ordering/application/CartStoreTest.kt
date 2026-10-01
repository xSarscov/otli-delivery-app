package com.otli.app.ordering.application

import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.SessionState
import com.otli.app.catalog.domain.Product
import com.otli.app.core.money.Money
import com.otli.app.ordering.domain.CartLine
import com.otli.app.ordering.domain.CartMerchant
import org.junit.Test

class CartStoreTest {
    private val store = CartStore()
    private val marta = CartMerchant("m1", "Comedor Marta")
    private val sol = CartMerchant("m2", "Pulperia Sol")
    private val nacatamal = product("p1", "Nacatamal", 12000)
    private val fresco = product("p2", "Fresco", 2500)
    private val vigoron = product("p9", "Vigoron", 8000)

    private fun product(id: String, name: String, cents: Long) =
        Product(id, "c1", name, "", Money(cents), isAvailable = true, photoVersion = 0)

    private val lines get() = store.state.value.cart.lines

    @Test
    fun aNewStoreHoldsAnEmptyCartWithNothingPending() {
        assertThat(store.state.value.cart.isEmpty).isTrue()
        assertThat(store.state.value.pending).isNull()
    }

    @Test
    fun productsFromTheSameMerchantAccumulateWithoutPrompting() {
        store.add(marta, nacatamal)
        store.add(marta, fresco)
        store.add(marta, nacatamal)

        assertThat(lines).containsExactly(
            CartLine("p1", "Nacatamal", Money(12000), 2),
            CartLine("p2", "Fresco", Money(2500), 1),
        ).inOrder()
        assertThat(store.state.value.cart.merchant).isEqualTo(marta)
        assertThat(store.state.value.pending).isNull()
    }

    @Test
    fun aProductFromAnotherMerchantLeavesTheCartAndAsksBeforeReplacing() {
        store.add(marta, nacatamal)

        store.add(sol, vigoron)

        assertThat(lines).containsExactly(CartLine("p1", "Nacatamal", Money(12000), 1))
        assertThat(store.state.value.pending).isEqualTo(PendingReplacement(current = marta, merchant = sol, product = vigoron))
    }

    @Test
    fun confirmingTheReplacementStartsOverWithTheNewProductForTheNewMerchant() {
        store.add(marta, nacatamal)
        store.add(marta, fresco)
        store.add(sol, vigoron)

        store.confirmReplacement()

        assertThat(lines).containsExactly(CartLine("p9", "Vigoron", Money(8000), 1))
        assertThat(store.state.value.cart.merchant).isEqualTo(sol)
        assertThat(store.state.value.pending).isNull()
    }

    @Test
    fun decliningTheReplacementKeepsTheCartUnchanged() {
        store.add(marta, nacatamal)
        store.add(sol, vigoron)

        store.dismissReplacement()

        assertThat(lines).containsExactly(CartLine("p1", "Nacatamal", Money(12000), 1))
        assertThat(store.state.value.cart.merchant).isEqualTo(marta)
        assertThat(store.state.value.pending).isNull()
    }

    @Test
    fun confirmingWithNothingPendingChangesNothing() {
        store.add(marta, nacatamal)

        store.confirmReplacement()

        assertThat(lines).containsExactly(CartLine("p1", "Nacatamal", Money(12000), 1))
    }

    @Test
    fun aSecondConflictReplacesThePendingOneWithTheLatestProduct() {
        store.add(marta, nacatamal)
        store.add(sol, vigoron)
        store.add(sol, fresco)

        assertThat(store.state.value.pending?.product).isEqualTo(fresco)
    }

    @Test
    fun addingFromTheCurrentMerchantWhileAQuestionIsPendingDiscardsTheQuestion() {
        store.add(marta, nacatamal)
        store.add(sol, vigoron)

        store.add(marta, fresco)

        assertThat(store.state.value.pending).isNull()
        assertThat(lines.map { it.productId }).containsExactly("p1", "p2").inOrder()
    }

    @Test
    fun setQuantityChangesOneLineAndZeroRemovesIt() {
        store.add(marta, nacatamal)
        store.add(marta, fresco)

        store.setQuantity("p1", 5)
        assertThat(lines.first { it.productId == "p1" }.quantity).isEqualTo(5)

        store.setQuantity("p1", 0)
        assertThat(lines.map { it.productId }).containsExactly("p2")
    }

    @Test
    fun removingTheLastLineFreesTheCartForAnyMerchant() {
        store.add(marta, nacatamal)
        store.setQuantity("p1", 0)

        store.add(sol, vigoron)

        assertThat(store.state.value.pending).isNull()
        assertThat(store.state.value.cart.merchant).isEqualTo(sol)
    }

    @Test
    fun clearEmptiesTheCartAndDropsAPendingQuestion() {
        store.add(marta, nacatamal)
        store.add(sol, vigoron)

        store.clear()

        assertThat(store.state.value.cart.isEmpty).isTrue()
        assertThat(store.state.value.pending).isNull()
    }

    @Test
    fun theCartSurvivesWhileTheCustomerSessionIsActiveOrLoading() {
        store.add(marta, nacatamal)

        store.onSessionChanged(SessionState.Active(Role.CUSTOMER))
        store.onSessionChanged(SessionState.Loading)

        assertThat(lines).hasSize(1)
    }

    @Test
    fun theCartIsClearedWhenTheSessionEndsOrBelongsToAnotherRole() {
        listOf(SessionState.SignedOut, SessionState.Active(Role.MERCHANT), SessionState.Suspended(Role.CUSTOMER)).forEach { session ->
            store.add(marta, nacatamal)

            store.onSessionChanged(session)

            assertThat(store.state.value.cart.isEmpty).isTrue()
        }
    }
}
