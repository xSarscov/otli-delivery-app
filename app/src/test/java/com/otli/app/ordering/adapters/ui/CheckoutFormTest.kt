package com.otli.app.ordering.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.map.MapPin
import com.otli.app.core.money.Money
import org.junit.Test

class CheckoutFormTest {
    private val pin = MapPin(12.27, -86.57)

    @Test
    fun aCompleteFormIsValidAndCarriesThePinAndTheTrimmedReference() {
        val result = CheckoutForm.validate(pin, "  Casa azul frente a la pulperia ", cashConfirmed = true)

        assertThat(result).isEqualTo(CheckoutFormResult.Valid(pin, "Casa azul frente a la pulperia"))
    }

    @Test
    fun aMissingPinIsReported() {
        val result = CheckoutForm.validate(null, "Casa azul", cashConfirmed = true)

        assertThat(result).isEqualTo(CheckoutFormResult.Invalid(CheckoutError.PinRequired))
    }

    @Test
    fun aBlankReferenceIsReported() {
        assertThat(CheckoutForm.validate(pin, "", cashConfirmed = true))
            .isEqualTo(CheckoutFormResult.Invalid(CheckoutError.ReferenceRequired))
        assertThat(CheckoutForm.validate(pin, "   ", cashConfirmed = true))
            .isEqualTo(CheckoutFormResult.Invalid(CheckoutError.ReferenceRequired))
    }

    @Test
    fun unconfirmedCashIsReported() {
        val result = CheckoutForm.validate(pin, "Casa azul", cashConfirmed = false)

        assertThat(result).isEqualTo(CheckoutFormResult.Invalid(CheckoutError.CashNotConfirmed))
    }

    @Test
    fun theFirstMissingFieldInScreenOrderWins() {
        assertThat(CheckoutForm.validate(null, "", cashConfirmed = false))
            .isEqualTo(CheckoutFormResult.Invalid(CheckoutError.PinRequired))
        assertThat(CheckoutForm.validate(pin, " ", cashConfirmed = false))
            .isEqualTo(CheckoutFormResult.Invalid(CheckoutError.ReferenceRequired))
    }

    @Test
    fun theTotalIsTheSubtotalPlusTheFeeOnceTheFeeIsKnown() {
        assertThat(CheckoutUiState(subtotal = Money(26500), fee = Money(3000)).total).isEqualTo(Money(29500))
        assertThat(CheckoutUiState(subtotal = Money(26500), fee = Money(4500)).total).isEqualTo(Money(31000))
    }

    @Test
    fun thereIsNoTotalWhileTheFeeIsUnknown() {
        assertThat(CheckoutUiState(subtotal = Money(26500), fee = null).total).isNull()
    }
}
