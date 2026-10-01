package com.otli.app.auth.adapters.firestore

import com.google.firebase.firestore.FieldValue
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.MerchantStoreDetails
import com.otli.app.auth.domain.Role

/** A document written next to `users/{uid}` in the registration batch; its id is the new user's uid. */
internal data class ProfileDocument(val collection: String, val data: Map<String, Any?>)

/**
 * The role-specific documents a self-registration writes in the same batch as `users/{uid}`, so a
 * role never has an account without its profile: a merchant's storefront and a courier's
 * availability slot (offline, no active order; the rules require exactly that start). Pure, so it is
 * JVM-testable.
 */
internal object RegistrationDocuments {
    const val MERCHANTS = "merchants"
    const val COURIERS = "couriers"

    fun profileDocuments(role: Role, status: AccountStatus, merchantStore: MerchantStoreDetails?): List<ProfileDocument> =
        when (role) {
            Role.MERCHANT -> listOfNotNull(merchantStore?.let { ProfileDocument(MERCHANTS, merchant(it, status)) })
            Role.COURIER -> listOf(ProfileDocument(COURIERS, courier()))
            Role.CUSTOMER, Role.ADMIN -> emptyList()
        }

    private fun merchant(store: MerchantStoreDetails, status: AccountStatus): Map<String, Any?> = mapOf(
        "name" to store.storeName,
        "description" to "",
        "phone" to store.phone,
        "status" to status.name.lowercase(),
        "isOpen" to false,
        "location" to mapOf("lat" to store.latitude, "lng" to store.longitude, "reference" to ""),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private fun courier(): Map<String, Any?> = mapOf(
        "isOnline" to false,
        "activeOrderId" to null,
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}
