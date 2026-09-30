package com.otli.app.ordering.domain

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.money.Money
import org.junit.Test

class CheckoutCalculatorTest {
    private fun line(id: String, cents: Long, quantity: Int) = CartLine(id, "Item $id", Money(cents), quantity)

    @Test
    fun theSubtotalMultipliesEachUnitPriceByItsQuantity() {
        val totals = CheckoutCalculator.totals(
            lines = listOf(line("p1", 12050, 2), line("p2", 2500, 3)),
            fee = Money(3000),
        )

        assertThat(totals.subtotal).isEqualTo(Money(12050 * 2 + 2500 * 3))
    }

    @Test
    fun theTotalIsTheSubtotalPlusTheFee() {
        val totals = CheckoutCalculator.totals(
            lines = listOf(line("p1", 12050, 2), line("p2", 2500, 3)),
            fee = Money(3000),
        )

        assertThat(totals).isEqualTo(Totals(subtotal = Money(31600), fee = Money(3000), total = Money(34600)))
    }

    @Test
    fun aDifferentFeeChangesOnlyTheFeeAndTheTotal() {
        val lines = listOf(line("p1", 8000, 1))

        val cheap = CheckoutCalculator.totals(lines, Money(2000))
        val dear = CheckoutCalculator.totals(lines, Money(4500))

        assertThat(cheap.subtotal).isEqualTo(dear.subtotal)
        assertThat(cheap.total).isEqualTo(Money(10000))
        assertThat(dear.total).isEqualTo(Money(12500))
    }

    @Test
    fun aZeroFeeLeavesTheTotalEqualToTheSubtotal() {
        val totals = CheckoutCalculator.totals(listOf(line("p1", 500, 4)), Money(0))

        assertThat(totals.fee).isEqualTo(Money(0))
        assertThat(totals.total).isEqualTo(totals.subtotal)
        assertThat(totals.total).isEqualTo(Money(2000))
    }

    @Test
    fun noLinesMeansAZeroSubtotalSoTheTotalIsJustTheFee() {
        val totals = CheckoutCalculator.totals(emptyList(), Money(3000))

        assertThat(totals).isEqualTo(Totals(subtotal = Money(0), fee = Money(3000), total = Money(3000)))
    }

    @Test
    fun theTotalInvariantHoldsAcrossVariedCarts() {
        val carts = listOf(
            listOf(line("a", 1, 1)),
            listOf(line("a", 99_999, 7), line("b", 1, 30)),
            listOf(line("a", 100, 2), line("b", 250, 2), line("c", 50, 1)),
        )
        for (lines in carts) for (fee in listOf(0L, 1L, 3000L)) {
            val totals = CheckoutCalculator.totals(lines, Money(fee))
            assertThat(totals.total).isEqualTo(totals.subtotal + totals.fee)
            assertThat(totals.fee).isEqualTo(Money(fee))
        }
    }
}
