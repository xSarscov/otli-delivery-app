package com.otli.app.catalog.adapters.firestore

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.otli.app.catalog.application.CatalogRepository
import com.otli.app.catalog.application.MerchantRepository
import com.otli.app.catalog.application.Storefront
import com.otli.app.catalog.domain.Category
import com.otli.app.catalog.domain.Product
import com.otli.app.core.firebase.await
import com.otli.app.core.result.suspendRunCatching
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine

/** `merchants/{uid}/categories`, `/products` and `/productPhotos` in Firestore (ADR-11). */
class FirestoreCatalogRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val merchants: MerchantRepository,
) : CatalogRepository {

    override fun observeCategories(merchantId: String): Flow<List<Category>> = callbackFlow {
        val registration = categories(merchantId).orderBy("sortOrder", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.documents.orEmpty().mapNotNull { CatalogDocuments.categoryFrom(it.id, it.data.orEmpty()) })
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun upsertCategory(merchantId: String, category: Category): Result<Unit> = suspendRunCatching {
        val collection = categories(merchantId)
        val document = if (category.id.isBlank()) collection.document() else collection.document(category.id)
        document.set(CatalogDocuments.categoryData(category)).await()
        Unit
    }

    override suspend fun removeCategory(merchantId: String, categoryId: String): Result<Unit> = suspendRunCatching {
        categories(merchantId).document(categoryId).delete().await()
        Unit
    }

    override fun observeProducts(merchantId: String): Flow<List<Product>> = callbackFlow {
        val registration = products(merchantId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                val products = snapshot?.documents.orEmpty().mapNotNull { CatalogDocuments.productFrom(it.id, it.data.orEmpty()) }
                trySend(products.sortedBy { it.name.lowercase() })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun upsertProduct(
        merchantId: String,
        product: Product,
        photoJpeg: ByteArray?,
    ): Result<Unit> = suspendRunCatching {
        val collection = products(merchantId)
        val document = if (product.id.isBlank()) collection.document() else collection.document(product.id)
        val version = CatalogDocuments.nextPhotoVersion(product.photoVersion, hasNewPhoto = photoJpeg != null)
        val batch = firestore.batch()
        batch.set(document, CatalogDocuments.productData(product, version))
        if (photoJpeg != null) {
            batch.set(photos(merchantId).document(document.id), CatalogDocuments.photoData(photoJpeg, version))
        }
        batch.commit().await()
        Unit
    }

    override suspend fun removeProduct(merchantId: String, productId: String): Result<Unit> = suspendRunCatching {
        val batch = firestore.batch()
        batch.delete(products(merchantId).document(productId))
        batch.delete(photos(merchantId).document(productId))
        batch.commit().await()
        Unit
    }

    override suspend fun setProductAvailability(
        merchantId: String,
        productId: String,
        available: Boolean,
    ): Result<Unit> = suspendRunCatching {
        products(merchantId).document(productId)
            .update(mapOf("isAvailable" to available, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
        Unit
    }

    override fun observeStorefront(merchantId: String): Flow<Storefront?> = combine(
        merchants.observeMerchant(merchantId),
        observeCategories(merchantId),
        observeProducts(merchantId),
    ) { merchant, categories, products -> merchant?.let { Storefront(it, categories, products) } }

    private fun merchantDoc(merchantId: String) = firestore.collection(CatalogDocuments.MERCHANTS).document(merchantId)

    private fun categories(merchantId: String): CollectionReference = merchantDoc(merchantId).collection("categories")

    private fun products(merchantId: String): CollectionReference = merchantDoc(merchantId).collection("products")

    private fun photos(merchantId: String): CollectionReference =
        merchantDoc(merchantId).collection(CatalogDocuments.PRODUCT_PHOTOS)
}
