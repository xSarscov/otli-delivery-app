package com.otli.app.catalog.adapters.firestore

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.adapters.firestore.FirestoreAuthRepository
import com.otli.app.catalog.domain.Category
import com.otli.app.catalog.domain.Product
import com.otli.app.core.di.OtliFirebase
import com.otli.app.core.firebase.await
import com.otli.app.core.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adapter proof against the emulators, signed in as the seeded active merchant `seed-merchant-1`
 * (needs the seed data that `test:android` loads). Every test cleans up what it creates.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreCatalogRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val auth = OtliFirebase.auth(context)
    private val firestore = OtliFirebase.firestore(context)
    private val repository = FirestoreCatalogRepository(firestore, FirestoreMerchantRepository(firestore))
    private val created = mutableListOf<String>()

    // Returns Unit so every `@Test fun x() = await { ... }` compiles to a void JUnit method.
    private fun await(block: suspend () -> Unit): Unit = runBlocking { withTimeout(30_000) { block() } }

    @Before
    fun signInAsSeedMerchant() = await {
        auth.signOut()
        FirestoreAuthRepository(auth, firestore).login("merchant1@otli.test", PASSWORD).getOrThrow()
    }

    @After
    fun cleanUp() = await {
        if (auth.currentUser != null) {
            created.forEach { repository.removeProduct(MERCHANT_1, it) }
            repository.setProductAvailability(MERCHANT_1, "prod-vigoron", false)
        }
        auth.signOut()
    }

    private suspend fun createProduct(name: String, price: Long, photo: ByteArray? = null): Product {
        repository.upsertProduct(
            MERCHANT_1,
            Product("", "cat-platos", name, "desc", Money(price), true, 0),
            photo,
        ).getOrThrow()
        val product = repository.observeProducts(MERCHANT_1).first { list -> list.any { it.name == name } }
            .first { it.name == name }
        created += product.id
        return product
    }

    @Test
    fun aNewProductRoundTripsWithItsNioPriceInCentavos() = await {
        val product = createProduct("Test Tostones", 4550)

        assertThat(product.price).isEqualTo(Money(4550))
        assertThat(product.categoryId).isEqualTo("cat-platos")
        assertThat(product.isAvailable).isTrue()
        assertThat(product.photoVersion).isEqualTo(0)
    }

    @Test
    fun editingAProductKeepsItsIdAndUpdatesTheFields() = await {
        val product = createProduct("Test Editable", 1000)

        repository.upsertProduct(MERCHANT_1, product.copy(name = "Test Editado", price = Money(1500))).getOrThrow()

        val edited = repository.observeProducts(MERCHANT_1).first { list -> list.any { it.id == product.id && it.name == "Test Editado" } }
            .first { it.id == product.id }
        assertThat(edited.price).isEqualTo(Money(1500))
    }

    @Test
    fun aPhotoBumpsThePhotoVersionAndStoresTheBytes() = await {
        val jpeg = byteArrayOf(5, 6, 7)
        val product = createProduct("Test Con Foto", 2000, photo = jpeg)

        assertThat(product.photoVersion).isEqualTo(1)
        val stored = firestore.collection("merchants").document(MERCHANT_1)
            .collection("productPhotos").document(product.id).get().await()
        assertThat(stored.getBlob("jpeg")?.toBytes()).isEqualTo(jpeg)
    }

    @Test
    fun theStoredPhotoCanBeFetchedThroughThePhotoSource() = await {
        val jpeg = byteArrayOf(4, 5, 6)
        val product = createProduct("Test Foto Leida", 2000, photo = jpeg)

        val source = FirestorePhotoSource(firestore)

        assertThat(source.fetch(MERCHANT_1, product.id)).isEqualTo(jpeg)
        assertThat(source.fetch(MERCHANT_1, "no-such-photo")).isNull()
    }

    @Test
    fun availabilityToggleIsReflectedInTheProductListener() = await {
        repository.setProductAvailability(MERCHANT_1, "prod-vigoron", true).getOrThrow()

        val products = repository.observeProducts(MERCHANT_1).first { list -> list.any { it.id == "prod-vigoron" && it.isAvailable } }
        assertThat(products.first { it.id == "prod-vigoron" }.isAvailable).isTrue()
    }

    @Test
    fun aCategoryRoundTripsAndCanBeRemoved() = await {
        repository.upsertCategory(MERCHANT_1, Category("", "Test Categoria", 99)).getOrThrow()
        val category = repository.observeCategories(MERCHANT_1).first { list -> list.any { it.name == "Test Categoria" } }
            .first { it.name == "Test Categoria" }
        assertThat(category.sortOrder).isEqualTo(99)

        repository.removeCategory(MERCHANT_1, category.id).getOrThrow()

        val remaining = repository.observeCategories(MERCHANT_1).first { list -> list.none { it.id == category.id } }
        assertThat(remaining.map { it.id }).containsAtLeast("cat-platos", "cat-bebidas")
    }

    @Test
    fun removingAProductDeletesItAndItsPhoto() = await {
        val product = createProduct("Test Borrable", 800, photo = byteArrayOf(1))

        repository.removeProduct(MERCHANT_1, product.id).getOrThrow()

        repository.observeProducts(MERCHANT_1).first { list -> list.none { it.id == product.id } }
        val photo = firestore.collection("merchants").document(MERCHANT_1)
            .collection("productPhotos").document(product.id).get().await()
        assertThat(photo.exists()).isFalse()
    }

    @Test
    fun aMerchantCannotWriteAnotherMerchantsCatalog() = await {
        val result = repository.upsertProduct(
            MERCHANT_2,
            Product("", "cat-pizzas", "Intruso", "x", Money(100), true, 0),
        )

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun theStorefrontCombinesMerchantCategoriesAndProducts() = await {
        val storefront = repository.observeStorefront(MERCHANT_1).first { it != null && it.products.isNotEmpty() }!!

        assertThat(storefront.merchant.id).isEqualTo(MERCHANT_1)
        assertThat(storefront.categories.map { it.id }).containsAtLeast("cat-platos", "cat-bebidas")
        assertThat(storefront.products.map { it.id }).contains("prod-nacatamal")
    }

    private companion object {
        const val MERCHANT_1 = "seed-merchant-1"
        const val MERCHANT_2 = "seed-merchant-2"
        const val PASSWORD = "otli-demo-123"
    }
}
