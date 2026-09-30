package com.otli.app.auth.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NicaraguanPhoneTest {
    @Test
    fun eightPlainDigitsNormalizeWithTheCountryPrefix() {
        assertThat(NicaraguanPhone.normalize("88880000")).isEqualTo("+50588880000")
        assertThat(NicaraguanPhone.normalize("22651234")).isEqualTo("+50522651234")
    }

    @Test
    fun spacesAndDashesAreTolerated() {
        assertThat(NicaraguanPhone.normalize("8888-0000")).isEqualTo("+50588880000")
        assertThat(NicaraguanPhone.normalize(" 8888 0000 ")).isEqualTo("+50588880000")
        assertThat(NicaraguanPhone.normalize("8-8-8-8 0000")).isEqualTo("+50588880000")
    }

    @Test
    fun anOptionalCountryPrefixIsAcceptedWithOrWithoutPlus() {
        assertThat(NicaraguanPhone.normalize("+505 8888 0000")).isEqualTo("+50588880000")
        assertThat(NicaraguanPhone.normalize("+505-8888-0000")).isEqualTo("+50588880000")
        assertThat(NicaraguanPhone.normalize("505 8888 0000")).isEqualTo("+50588880000")
    }

    @Test
    fun wrongLengthsAreRejected() {
        assertThat(NicaraguanPhone.normalize("8888000")).isNull()
        assertThat(NicaraguanPhone.normalize("888800001")).isNull()
        assertThat(NicaraguanPhone.normalize("+505 8888 000")).isNull()
        assertThat(NicaraguanPhone.normalize("+505 8888 00000")).isNull()
    }

    @Test
    fun nonDigitsAndForeignPrefixesAreRejected() {
        assertThat(NicaraguanPhone.normalize("")).isNull()
        assertThat(NicaraguanPhone.normalize("   ")).isNull()
        assertThat(NicaraguanPhone.normalize("8888-abcd")).isNull()
        assertThat(NicaraguanPhone.normalize("+506 8888 0000")).isNull()
        assertThat(NicaraguanPhone.normalize("(505) 8888 0000")).isNull()
    }

    @Test
    fun isValidMirrorsNormalize() {
        assertThat(NicaraguanPhone.isValid("8888 0000")).isTrue()
        assertThat(NicaraguanPhone.isValid("1234")).isFalse()
    }
}
