package com.otli.app.ordering.domain

/** Lifecycle of an order. [wire] is the value stored in Firestore and in the transitions contract. */
enum class OrderStatus(val wire: String) {
    PLACED("placed"),
    ACCEPTED("accepted"),
    PREPARING("preparing"),
    READY("ready"),
    CLAIMED("claimed"),
    PICKED_UP("picked_up"),
    DELIVERED("delivered"),
    REJECTED("rejected"),
    CANCELLED("cancelled"),
    ;

    /** No transition leaves a terminal status. */
    val isTerminal: Boolean get() = this == DELIVERED || this == REJECTED || this == CANCELLED

    companion object {
        fun fromWire(value: String): OrderStatus? = entries.firstOrNull { it.wire == value }
    }
}
