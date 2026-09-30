package com.otli.app.ordering.domain

import com.google.common.truth.Truth.assertThat
import com.otli.app.catalog.domain.Product
import com.otli.app.core.money.Money
import org.junit.Assert.assertThrows
import org.junit.Test

class CartTest {
    private val marta = CartMerchant("m1", "Comedor Marta")
    private val sol = CartMerchant("m2", "Pulperia Sol")

    private fun product(id: String, name: String = "Item $id", cents: Long = 1000) =
        Product(id, "c1", name, "", Money(cents), isAvailable = true, photoVersion = 0)

    private fun Cart.added(merchant: CartMerchant, product: Product, quantity: Int = 1): Cart =
        (add(merchant, product, quantity) as AddResult.Added).cart

    @Test
    fun anEmptyCartAdoptsTheMerchantOfTheFirstProduct() {
        val result = Cart.EMPTY.add(marta, product("p1", "Nacatamal", 12050))

        val cart = (result as AddResult.Added).cart
        assertThat(cart.merchant).isEqualTo(marta)
        assertThat(cart.lines).containsExactly(CartLine("p1", "Nacatamal", Money(12050), 1))
        assertThat(cart.isEmpty).isFalse()
    }

    @Test
    fun aProductFromTheSameMerchantIsAddedWithoutPrompting() {
        val cart = Cart.EMPTY.added(marta, product("p1")).added(marta, product("p2"))

        assertThat(cart.lines.map { it.productId }).containsExactly("p1", "p2").inOrder()
        assertThat(cart.merchant).isEqualTo(marta)
    }

    @Test
    fun addingTheSameProductAgainRaisesItsQuantityInsteadOfDuplicatingTheLine() {
        val cart = Cart.EMPTY.added(marta, product("p1")).added(marta, product("p2")).added(marta, product("p1"), 2)

        assertThat(cart.lines.map { it.productId }).containsExactly("p1", "p2").inOrder()
        assertThat(cart.lines.first { it.productId == "p1" }.quantity).isEqualTo(3)
        assertThat(cart.itemCount).isEqualTo(4)
    }

    @Test
    fun aProductFromAnotherMerchantReportsTheConflictAndAddsNothing() {
        val cart = Cart.EMPTY.added(marta, product("p1"))

        val result = cart.add(sol, product("p9", "Fresco"))

        assertThat(result).isEqualTo(AddResult.ConflictingMerchant(current = marta))
        assertThat(cart.lines.map { it.productId }).containsExactly("p1")
        assertThat(cart.merchant).isEqualTo(marta)
    }

    @Test
    fun decliningTheConflictKeepsTheCartUsableForItsOwnMerchant() {
        val cart = Cart.EMPTY.added(marta, product("p1"))
        assertThat(cart.add(sol, product("p9"))).isInstanceOf(AddResult.ConflictingMerchant::class.java)

        val next = cart.added(marta, product("p2"))

        assertThat(next.lines.map { it.productId }).containsExactly("p1", "p2").inOrder()
    }

    @Test
    fun confirmingTheConflictReplacesTheCartWithTheNewMerchantsItem() {
        val cart = Cart.EMPTY.added(marta, product("p1")).added(marta, product("p2"))

        val replaced = cart.replaceWith(sol, product("p9", "Fresco", 2500))

        assertThat(replaced.merchant).isEqualTo(sol)
        assertThat(replaced.lines).containsExactly(CartLine("p9", "Fresco", Money(2500), 1))
    }

    @Test
    fun settingAQuantityUpdatesOnlyThatLine() {
        val cart = Cart.EMPTY.added(marta, product("p1")).added(marta, product("p2")).setQuantity("p2", 5)

        assertThat(cart.lines.first { it.productId == "p1" }.quantity).isEqualTo(1)
        assertThat(cart.lines.first { it.productId == "p2" }.quantity).isEqualTo(5)
    }

    @Test
    fun settingTheQuantityToZeroRemovesTheLine() {
        val cart = Cart.EMPTY.added(marta, product("p1")).added(marta, product("p2")).setQuantity("p1", 0)

        assertThat(cart.lines.map { it.productId }).containsExactly("p2")
        assertThat(cart.merchant).isEqualTo(marta)
    }

    @Test
    fun removingTheLastLineEmptiesTheCartAndFreesItFromItsMerchant() {
        val cart = Cart.EMPTY.added(marta, product("p1")).setQuantity("p1", 0)

        assertThat(cart.isEmpty).isTrue()
        assertThat(cart.merchant).isNull()
        assertThat(cart.add(sol, product("p9"))).isInstanceOf(AddResult.Added::class.java)
    }

    @Test
    fun settingTheQuantityOfAProductNotInTheCartChangesNothing() {
        val cart = Cart.EMPTY.added(marta, product("p1"))

        assertThat(cart.setQuantity("missing", 4)).isEqualTo(cart)
    }

    @Test
    fun clearingEmptiesTheCart() {
        val cart = Cart.EMPTY.added(marta, product("p1")).clear()

        assertThat(cart).isEqualTo(Cart.EMPTY)
    }

    @Test
    fun aQuantityBelowOneCannotBeAdded() {
        assertThrows(IllegalArgumentException::class.java) { Cart.EMPTY.add(marta, product("p1"), 0) }
    }
}
