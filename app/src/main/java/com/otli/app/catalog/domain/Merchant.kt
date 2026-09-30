package com.otli.app.catalog.domain

import com.otli.app.auth.domain.AccountStatus

/** Where the merchant's storefront is; also the pickup point of its orders. */
data class MerchantLocation(val latitude: Double, val longitude: Double, val reference: String)

/**
 * The `merchants/{uid}` document. [status] mirrors the owner's `users` status so customers can
 * list active merchants with one query (design: Firestore Data Model). [photoVersion] is 0 when
 * the merchant has no photo and changes on every replacement so image caches are invalidated.
 */
data class Merchant(
    val id: String,
    val name: String,
    val description: String,
    val phone: String,
    val status: AccountStatus,
    val isOpen: Boolean,
    val photoVersion: Int,
    val location: MerchantLocation?,
)
