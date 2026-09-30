package com.otli.app.catalog.adapters.ui

import com.otli.app.auth.domain.AccountStatus
import com.otli.app.catalog.application.CatalogRepository
import com.otli.app.catalog.application.MerchantRepository
import com.otli.app.catalog.application.PhotoCompressor
import com.otli.app.catalog.application.Storefront
import com.otli.app.catalog.domain.Category
import com.otli.app.catalog.domain.Merchant
import com.otli.app.catalog.domain.MerchantLocation
import com.otli.app.catalog.domain.Product
import com.otli.app.core.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

fun aMerchant(
    id: String = "m1",
    name: String = "Comedor Marta",
    description: String = "Comida tipica",
    phone: String = "+50588880101",
    isOpen: Boolean = true,
    photoVersion: Int = 0,
) = Merchant(
    id = id,
    name = name,
    description = description,
    phone = phone,
    status = AccountStatus.ACTIVE,
    isOpen = isOpen,
    photoVersion = photoVersion,
    location = MerchantLocation(12.2667, -86.5667, ""),
)

/** In-memory merchant document: successful writes are reflected in [merchant], like a live listener. */
class FakeMerchantRepository(initial: Merchant? = aMerchant()) : MerchantRepository {
    data class ProfileUpdate(val merchantId: String, val name: String, val description: String, val phone: String)

    val merchant = MutableStateFlow(initial)
    val profileUpdates = mutableListOf<ProfileUpdate>()
    val openChanges = mutableListOf<Boolean>()
    val photos = mutableListOf<ByteArray>()
    var writeOutcome: suspend () -> Result<Unit> = { Result.success(Unit) }

    override fun observeMerchant(merchantId: String): Flow<Merchant?> = merchant

    override suspend fun updateProfile(
        merchantId: String,
        name: String,
        description: String,
        phone: String,
    ): Result<Unit> {
        profileUpdates += ProfileUpdate(merchantId, name, description, phone)
        return writeOutcome().onSuccess { merchant.value = merchant.value?.copy(name = name, description = description, phone = phone) }
    }

    override suspend fun updatePhoto(merchantId: String, jpeg: ByteArray): Result<Unit> {
        photos += jpeg
        return writeOutcome().onSuccess { merchant.value = merchant.value?.let { it.copy(photoVersion = it.photoVersion + 1) } }
    }

    override suspend fun setOpen(merchantId: String, open: Boolean): Result<Unit> {
        openChanges += open
        return writeOutcome().onSuccess { merchant.value = merchant.value?.copy(isOpen = open) }
    }

    /** The customer-facing list; replace with a failing or silent flow to test error and loading states. */
    var merchantsList: Flow<List<Merchant>> = MutableStateFlow(emptyList())

    override fun observeMerchantsList(): Flow<List<Merchant>> = merchantsList
}

fun aCategory(id: String = "c1", name: String = "Platos", sortOrder: Int = 1) = Category(id, name, sortOrder)

fun aProduct(
    id: String = "p1",
    categoryId: String = "c1",
    name: String = "Nacatamal",
    price: Long = 12000,
    isAvailable: Boolean = true,
    photoVersion: Int = 0,
) = Product(id, categoryId, name, "desc", Money(price), isAvailable, photoVersion)

/** In-memory catalog: successful writes update the live lists, like Firestore listeners would. */
class FakeCatalogRepository(
    categories: List<Category> = listOf(aCategory("c1", "Platos", 1), aCategory("c2", "Bebidas", 2)),
    products: List<Product> = listOf(aProduct("p1", "c1"), aProduct("p2", "c2", "Fresco", 2500, isAvailable = false)),
) : CatalogRepository {
    data class ProductWrite(val merchantId: String, val product: Product, val photo: ByteArray?)

    val categories = MutableStateFlow(categories)
    val products = MutableStateFlow(products)
    val categoryWrites = mutableListOf<Category>()
    val productWrites = mutableListOf<ProductWrite>()
    val removedCategories = mutableListOf<String>()
    val removedProducts = mutableListOf<String>()
    val availabilityChanges = mutableListOf<Pair<String, Boolean>>()
    var writeOutcome: suspend () -> Result<Unit> = { Result.success(Unit) }

    override fun observeCategories(merchantId: String): Flow<List<Category>> = categories

    override suspend fun upsertCategory(merchantId: String, category: Category): Result<Unit> {
        categoryWrites += category
        return writeOutcome()
    }

    override suspend fun removeCategory(merchantId: String, categoryId: String): Result<Unit> {
        removedCategories += categoryId
        return writeOutcome()
    }

    override fun observeProducts(merchantId: String): Flow<List<Product>> = products

    override suspend fun upsertProduct(merchantId: String, product: Product, photoJpeg: ByteArray?): Result<Unit> {
        productWrites += ProductWrite(merchantId, product, photoJpeg)
        return writeOutcome()
    }

    override suspend fun removeProduct(merchantId: String, productId: String): Result<Unit> {
        removedProducts += productId
        return writeOutcome()
    }

    override suspend fun setProductAvailability(merchantId: String, productId: String, available: Boolean): Result<Unit> {
        availabilityChanges += productId to available
        return writeOutcome().onSuccess {
            products.value = products.value.map { if (it.id == productId) it.copy(isAvailable = available) else it }
        }
    }

    /** What a customer would see; replace with a failing or silent flow to test error and loading states. */
    var storefront: Flow<Storefront?> = MutableStateFlow(null)
    val storefrontRequests = mutableListOf<String>()

    override fun observeStorefront(merchantId: String): Flow<Storefront?> {
        storefrontRequests += merchantId
        return storefront
    }
}

/** Returns the input reversed so tests can tell compressed bytes from picked bytes. */
class FakePhotoCompressor : PhotoCompressor {
    var outcome: (ByteArray) -> Result<ByteArray> = { Result.success(it.reversedArray()) }

    override fun compress(source: ByteArray): Result<ByteArray> = outcome(source)
}
