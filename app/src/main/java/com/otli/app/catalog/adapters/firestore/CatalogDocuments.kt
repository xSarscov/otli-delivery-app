package com.otli.app.catalog.adapters.firestore

import com.google.firebase.firestore.FieldValue
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.catalog.domain.Merchant
import com.otli.app.catalog.domain.MerchantLocation

/**
 * Pure translation between Firestore document data and catalog domain types, kept free of
 * snapshot objects so it is JVM-testable. Field names follow the design's data model table.
 */
internal object CatalogDocuments {
    const val MERCHANTS = "merchants"

    /** Fixed id of the merchant profile photo inside `productPhotos` (ADR-11). */
    const val PROFILE_PHOTO_ID = "profile"
    const val PRODUCT_PHOTOS = "productPhotos"

    /** Returns null when the document is not a recognizable merchant (no name or unknown status). */
    fun merchantFrom(id: String, data: Map<String, Any?>): Merchant? {
        val name = data["name"] as? String ?: return null
        val status = AccountStatus.entries.firstOrNull { it.name.lowercase() == data["status"] } ?: return null
        return Merchant(
            id = id,
            name = name,
            description = data["description"] as? String ?: "",
            phone = data["phone"] as? String ?: "",
            status = status,
            isOpen = data["isOpen"] as? Boolean ?: false,
            photoVersion = (data["photoVersion"] as? Number)?.toInt() ?: 0,
            location = locationFrom(data["location"]),
        )
    }

    /** Owner edit of the profile fields; never carries `status` (Admin-only in the rules). */
    fun profileUpdate(name: String, description: String, phone: String): Map<String, Any> = mapOf(
        "name" to name,
        "description" to description,
        "phone" to phone,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private fun locationFrom(raw: Any?): MerchantLocation? {
        val map = raw as? Map<*, *> ?: return null
        val latitude = (map["lat"] as? Number)?.toDouble() ?: return null
        val longitude = (map["lng"] as? Number)?.toDouble() ?: return null
        return MerchantLocation(latitude, longitude, map["reference"] as? String ?: "")
    }
}
