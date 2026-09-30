package com.otli.app.catalog.adapters.firestore

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.otli.app.catalog.application.MerchantRepository
import com.otli.app.catalog.domain.Merchant
import com.otli.app.core.firebase.await
import com.otli.app.core.result.suspendRunCatching
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** `merchants/{uid}` in Firestore; the profile photo lives at `productPhotos/profile` (ADR-11). */
class FirestoreMerchantRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : MerchantRepository {

    override fun observeMerchant(merchantId: String): Flow<Merchant?> = callbackFlow {
        val registration = merchantDoc(merchantId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                trySend(snapshot?.data?.let { CatalogDocuments.merchantFrom(snapshot.id, it) })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun updateProfile(
        merchantId: String,
        name: String,
        description: String,
        phone: String,
    ): Result<Unit> = suspendRunCatching {
        merchantDoc(merchantId).update(CatalogDocuments.profileUpdate(name, description, phone)).await()
        Unit
    }

    override suspend fun updatePhoto(merchantId: String, jpeg: ByteArray): Result<Unit> = suspendRunCatching {
        val merchant = merchantDoc(merchantId)
        val photo = merchant.collection(CatalogDocuments.PRODUCT_PHOTOS).document(CatalogDocuments.PROFILE_PHOTO_ID)
        // Read-then-write so the version only ever goes up, even across concurrent edits.
        firestore.runTransaction { transaction ->
            val next = (transaction.get(merchant).getLong("photoVersion") ?: 0L) + 1
            transaction.set(photo, mapOf("jpeg" to Blob.fromBytes(jpeg), "version" to next))
            transaction.update(merchant, mapOf("photoVersion" to next, "updatedAt" to FieldValue.serverTimestamp()))
            null
        }.await()
        Unit
    }

    override suspend fun setOpen(merchantId: String, open: Boolean): Result<Unit> = suspendRunCatching {
        merchantDoc(merchantId)
            .update(mapOf("isOpen" to open, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
        Unit
    }

    override fun observeMerchantsList(): Flow<List<Merchant>> = callbackFlow {
        val registration = firestore.collection(CatalogDocuments.MERCHANTS)
            .whereEqualTo("status", "active")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.documents.orEmpty().mapNotNull { CatalogDocuments.merchantFrom(it.id, it.data.orEmpty()) })
                }
            }
        awaitClose { registration.remove() }
    }

    private fun merchantDoc(merchantId: String) = firestore.collection(CatalogDocuments.MERCHANTS).document(merchantId)
}
