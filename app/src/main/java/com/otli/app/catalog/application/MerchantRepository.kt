package com.otli.app.catalog.application

import com.otli.app.catalog.domain.Merchant
import kotlinx.coroutines.flow.Flow

/** Port over `merchants/{uid}`: the owner's profile and the customer-facing merchant list. */
interface MerchantRepository {
    /** Emits the merchant profile, or null while the document does not exist. */
    fun observeMerchant(merchantId: String): Flow<Merchant?>

    /** Owner edit of name, description and phone. Never touches `status` (Admin-only). */
    suspend fun updateProfile(merchantId: String, name: String, description: String, phone: String): Result<Unit>

    /** Owner replaces the merchant photo; [jpeg] is already compressed (ADR-11). */
    suspend fun updatePhoto(merchantId: String, jpeg: ByteArray): Result<Unit>

    suspend fun setOpen(merchantId: String, open: Boolean): Result<Unit>

    /** Active merchants for the customer browsing screen, ordered by name. */
    fun observeMerchantsList(): Flow<List<Merchant>>
}
