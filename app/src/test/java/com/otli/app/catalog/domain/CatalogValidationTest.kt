package com.otli.app.catalog.domain

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.money.Money
import org.junit.Test

class CatalogValidationTest {
    @Test
    fun blankNamesAreRejected() {
        val blanks = listOf("", " ", "   ", "\t\n")
        for (name in blanks) {
            assertThat(CatalogValidation.validateName(name))
                .isEqualTo(CatalogValidationError.BLANK_NAME)
        }
    }

    @Test
    fun nonBlankNamesAreAccepted() {
        val names = listOf("Baho", "  Tacos  ", "Café con leche")
        for (name in names) {
            assertThat(CatalogValidation.validateName(name)).isNull()
        }
    }

    @Test
    fun zeroPriceIsRejected() {
        assertThat(CatalogValidation.validatePrice(Money(0)))
            .isEqualTo(CatalogValidationError.NON_POSITIVE_PRICE)
    }

    @Test
    fun positivePricesAreAccepted() {
        val prices = listOf(1L, 3_500L, 1_000_000L)
        for (centavos in prices) {
            assertThat(CatalogValidation.validatePrice(Money(centavos))).isNull()
        }
    }

    @Test
    fun negativeAmountsCannotBeRepresentedAsPrices() {
        // Money itself rejects negatives (ADR-9), so a "negative price" never reaches validation.
        val result = runCatching { Money(-1) }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun validProductHasNoErrors() {
        assertThat(CatalogValidation.validateProduct("Gallo pinto", Money(9_000))).isEmpty()
    }

    @Test
    fun productWithBlankNameReportsOnlyTheNameError() {
        assertThat(CatalogValidation.validateProduct(" ", Money(9_000)))
            .containsExactly(CatalogValidationError.BLANK_NAME)
    }

    @Test
    fun productWithZeroPriceReportsOnlyThePriceError() {
        assertThat(CatalogValidation.validateProduct("Gallo pinto", Money(0)))
            .containsExactly(CatalogValidationError.NON_POSITIVE_PRICE)
    }

    @Test
    fun productWithBothProblemsReportsBothErrors() {
        assertThat(CatalogValidation.validateProduct("", Money(0)))
            .containsExactly(
                CatalogValidationError.BLANK_NAME,
                CatalogValidationError.NON_POSITIVE_PRICE,
            )
    }
}
