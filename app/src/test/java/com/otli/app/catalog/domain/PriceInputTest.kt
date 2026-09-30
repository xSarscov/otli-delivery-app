package com.otli.app.catalog.domain

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.money.Money
import org.junit.Test

class PriceInputTest {
    @Test
    fun wholeAndDecimalAmountsParseToCentavos() {
        assertThat(PriceInput.parse("45")).isEqualTo(Money(4500))
        assertThat(PriceInput.parse("45.5")).isEqualTo(Money(4550))
        assertThat(PriceInput.parse("45.50")).isEqualTo(Money(4550))
        assertThat(PriceInput.parse("0.05")).isEqualTo(Money(5))
    }

    @Test
    fun aCommaDecimalSeparatorAndSurroundingSpacesAreTolerated() {
        assertThat(PriceInput.parse(" 45,50 ")).isEqualTo(Money(4550))
        assertThat(PriceInput.parse("120,5")).isEqualTo(Money(12050))
    }

    @Test
    fun zeroIsParsedSoTheDomainRuleCanRejectIt() {
        assertThat(PriceInput.parse("0")).isEqualTo(Money(0))
        assertThat(CatalogValidation.validatePrice(PriceInput.parse("0")!!)).isEqualTo(CatalogValidationError.NON_POSITIVE_PRICE)
    }

    @Test
    fun negativeGarbageAndTooManyDecimalsAreRejected() {
        assertThat(PriceInput.parse("-5")).isNull()
        assertThat(PriceInput.parse("abc")).isNull()
        assertThat(PriceInput.parse("")).isNull()
        assertThat(PriceInput.parse("4.555")).isNull()
        assertThat(PriceInput.parse("4.")).isNull()
        assertThat(PriceInput.parse("1.2.3")).isNull()
        assertThat(PriceInput.parse("C\$ 45")).isNull()
    }

    @Test
    fun absurdlyLargeAmountsAreRejectedInsteadOfOverflowing() {
        assertThat(PriceInput.parse("99999999999999999999")).isNull()
    }

    @Test
    fun formatShowsTwoDecimalsAndRoundTripsThroughParse() {
        assertThat(PriceInput.format(Money(4550))).isEqualTo("45.50")
        assertThat(PriceInput.format(Money(5))).isEqualTo("0.05")
        assertThat(PriceInput.format(Money(4500))).isEqualTo("45.00")
        assertThat(PriceInput.parse(PriceInput.format(Money(12345)))).isEqualTo(Money(12345))
    }
}
