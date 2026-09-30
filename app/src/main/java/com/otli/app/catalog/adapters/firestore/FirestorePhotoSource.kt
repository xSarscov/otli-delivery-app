package com.otli.app.catalog.adapters.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.otli.app.catalog.application.PhotoSource
import com.otli.app.core.firebase.await
import javax.inject.Inject

/** Reads `merchants/{mid}/productPhotos/{photoId}` (ADR-11); rules let any signed-in user read it. */
class FirestorePhotoSource @Inject constructor(private val firestore: FirebaseFirestore) : PhotoSource {
    override suspend fun fetch(merchantId: String, photoId: String): ByteArray? {
        val snapshot = firestore.collection(CatalogDocuments.MERCHANTS).document(merchantId)
            .collection(CatalogDocuments.PRODUCT_PHOTOS).document(photoId).get().await()
        return CatalogDocuments.photoBytesFrom(snapshot.data)
    }
}
