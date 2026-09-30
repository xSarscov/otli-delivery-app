package com.otli.app.core.money

/** Non-negative amount in NIO centavos (C$ 1.00 = 100). See ADR-9. */
@JvmInline
value class Money(val centavos: Long) {
    init {
        require(centavos >= 0) { "Money cannot be negative: $centavos" }
    }

    operator fun plus(other: Money) = Money(centavos + other.centavos)

    /** Multiplies by a quantity; a negative quantity fails the non-negative invariant. */
    operator fun times(qty: Int) = Money(centavos * qty)
}
