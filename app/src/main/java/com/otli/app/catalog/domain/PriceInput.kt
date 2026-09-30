package com.otli.app.catalog.domain

import com.otli.app.core.money.Money

/** Turns what a merchant types into [Money] (C$ with up to two decimals) and back. */
object PriceInput {
    private val amount = Regex("""(\d{1,9})(?:\.(\d{1,2}))?""")
    private const val CENTAVOS_PER_CORDOBA = 100

    /** Returns null for anything that is not a plain non-negative amount; zero is left to validation. */
    fun parse(text: String): Money? {
        val match = amount.matchEntire(text.trim().replace(',', '.')) ?: return null
        val whole = match.groupValues[1].toLong()
        val fraction = match.groupValues[2].padEnd(2, '0').toLong()
        return Money(whole * CENTAVOS_PER_CORDOBA + fraction)
    }

    /** Always two decimals, so a parsed price shows back exactly as it will be stored. */
    fun format(price: Money): String {
        val whole = price.centavos / CENTAVOS_PER_CORDOBA
        val fraction = (price.centavos % CENTAVOS_PER_CORDOBA).toString().padStart(2, '0')
        return "$whole.$fraction"
    }
}
