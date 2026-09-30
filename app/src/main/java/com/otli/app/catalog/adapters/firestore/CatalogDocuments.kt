package com.otli.app.catalog.adapters.firestore

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.catalog.domain.Category
import com.otli.app.catalog.domain.Merchant
import com.otli.app.catalog.domain.MerchantLocation
import com.otli.app.catalog.domain.Product
import com.otli.app.core.money.Money

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

    fun categoryFrom(id: String, data: Map<String, Any?>): Category? {
        val name = data["name"] as? String ?: return null
        return Category(id, name, (data["sortOrder"] as? Number)?.toInt() ?: 0)
    }

    fun categoryData(category: Category): Map<String, Any> =
        mapOf("name" to category.name, "sortOrder" to category.sortOrder)

    /** Returns null for a document missing its name or category, or carrying a negative price. */
    fun productFrom(id: String, data: Map<String, Any?>): Product? {
        val name = data["name"] as? String ?: return null
        val categoryId = data["categoryId"] as? String ?: return null
        val centavos = (data["priceCents"] as? Number)?.toLong()?.takeIf { it >= 0 } ?: return null
        return Product(
            id = id,
            categoryId = categoryId,
            name = name,
            description = data["description"] as? String ?: "",
            price = Money(centavos),
            isAvailable = data["isAvailable"] as? Boolean ?: false,
            photoVersion = (data["photoVersion"] as? Number)?.toInt() ?: 0,
        )
    }

    /** Every field the rules require on a product; the price is stored as integer centavos (ADR-9). */
    fun productData(product: Product, photoVersion: Int): Map<String, Any> = mapOf(
        "categoryId" to product.categoryId,
        "name" to product.name,
        "description" to product.description,
        "priceCents" to product.price.centavos,
        "isAvailable" to product.isAvailable,
        "photoVersion" to photoVersion,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun photoData(jpeg: ByteArray, version: Int): Map<String, Any> =
        mapOf("jpeg" to Blob.fromBytes(jpeg), "version" to version)

    /** The version only moves when a new photo is written, so caches are invalidated exactly then. */
    fun nextPhotoVersion(current: Int, hasNewPhoto: Boolean): Int = if (hasNewPhoto) current + 1 else current

    private fun locationFrom(raw: Any?): MerchantLocation? {
        val map = raw as? Map<*, *> ?: return null
        val latitude = (map["lat"] as? Number)?.toDouble() ?: return null
        val longitude = (map["lng"] as? Number)?.toDouble() ?: return null
        return MerchantLocation(latitude, longitude, map["reference"] as? String ?: "")
    }
}
