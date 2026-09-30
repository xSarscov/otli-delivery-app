package com.otli.app.ordering.domain

/** Who performs an order transition; the transitions contract names them by [wire]. */
enum class Actor(val wire: String) {
    CUSTOMER("customer"),
    MERCHANT("merchant"),
    COURIER("courier"),
    ADMIN("admin"),
    ;

    companion object {
        fun fromWire(value: String): Actor? = entries.firstOrNull { it.wire == value }
    }
}
