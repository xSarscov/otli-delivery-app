package com.otli.app.catalog.application

import com.otli.app.catalog.domain.Category
import com.otli.app.catalog.domain.Merchant
import com.otli.app.catalog.domain.Product
import kotlinx.coroutines.flow.Flow

/** What a customer sees when opening one merchant: profile, categories and products. */
data class Storefront(
    val merchant: Merchant,
    val categories: List<Category>,
    val products: List<Product>,
)

/** Port over `merchants/{uid}/categories`, `/products` and `/productPhotos`. */
interface CatalogRepository {
    fun observeCategories(merchantId: String): Flow<List<Category>>

    /** Creates the category when [Category.id] is blank, otherwise updates it. */
    suspend fun upsertCategory(merchantId: String, category: Category): Result<Unit>

    suspend fun removeCategory(merchantId: String, categoryId: String): Result<Unit>

    fun observeProducts(merchantId: String): Flow<List<Product>>

    /**
     * Creates the product when [Product.id] is blank, otherwise updates it. A non-null [photoJpeg]
     * replaces the stored photo and bumps `photoVersion`.
     */
    suspend fun upsertProduct(merchantId: String, product: Product, photoJpeg: ByteArray? = null): Result<Unit>

    suspend fun removeProduct(merchantId: String, productId: String): Result<Unit>

    suspend fun setProductAvailability(merchantId: String, productId: String, available: Boolean): Result<Unit>

    /** Emits the merchant with its categories and products, or null if the merchant is missing. */
    fun observeStorefront(merchantId: String): Flow<Storefront?>
}
