package com.otli.app.catalog.domain

import com.otli.app.core.money.Money

/** A sellable item. [photoVersion] is 0 when there is no photo (ADR-11). */
data class Product(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String,
    val price: Money,
    val isAvailable: Boolean,
    val photoVersion: Int,
)
