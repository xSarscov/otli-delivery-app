package com.otli.app.catalog.application

/**
 * Identifies one stored photo. [version] mirrors `photoVersion` (ADR-11): it is 0 when nothing is
 * stored and changes on every replacement, so it doubles as the cache-busting part of the key.
 */
data class PhotoKey(val merchantId: String, val photoId: String, val version: Int) {
    companion object {
        /** Fixed id of the merchant profile photo inside `productPhotos` (ADR-11). */
        const val PROFILE_PHOTO_ID = "profile"

        fun product(merchantId: String, productId: String, version: Int) = PhotoKey(merchantId, productId, version)

        fun merchantProfile(merchantId: String, version: Int) = PhotoKey(merchantId, PROFILE_PHOTO_ID, version)
    }
}

/** Port over `merchants/{mid}/productPhotos/{photoId}`: the compressed JPEG bytes of one photo. */
fun interface PhotoSource {
    /** Returns the stored JPEG bytes, or null when the photo does not exist. */
    suspend fun fetch(merchantId: String, photoId: String): ByteArray?
}
