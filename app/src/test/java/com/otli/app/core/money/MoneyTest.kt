package com.otli.app.core.money

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test
    fun plusAddsCentavos() {
        val cases = listOf(
            Triple(0L, 0L, 0L),
            Triple(100L, 250L, 350L),
            Triple(3000L, 12_050L, 15_050L),
        )
        for ((a, b, expected) in cases) {
            assertThat(Money(a) + Money(b)).isEqualTo(Money(expected))
        }
    }

    @Test
    fun timesMultipliesByQuantity() {
        val cases = listOf(
            Triple(100L, 0, 0L),
            Triple(100L, 1, 100L),
            Triple(4_550L, 3, 13_650L),
        )
        for ((unit, qty, expected) in cases) {
            assertThat(Money(unit) * qty).isEqualTo(Money(expected))
        }
    }

    @Test
    fun negativeCentavosAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { Money(-1L) }
        assertThrows(IllegalArgumentException::class.java) { Money(-3000L) }
    }

    @Test
    fun timesNegativeQuantityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { Money(100L) * -2 }
    }
}
