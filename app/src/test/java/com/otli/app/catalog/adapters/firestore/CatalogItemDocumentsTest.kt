package com.otli.app.catalog.adapters.firestore

import com.google.common.truth.Truth.assertThat
import com.otli.app.catalog.domain.Category
import com.otli.app.catalog.domain.Product
import com.otli.app.core.money.Money
import org.junit.Test

class CatalogItemDocumentsTest {
    private val productDocument: Map<String, Any?> = mapOf(
        "categoryId" to "cat-platos",
        "name" to "Nacatamal",
        "description" to "Cerdo, arroz y papa",
        "priceCents" to 12000L,
        "isAvailable" to true,
        "photoVersion" to 2L,
    )

    @Test
    fun aCategoryDocumentMapsNameAndSortOrder() {
        val category = CatalogDocuments.categoryFrom("c1", mapOf("name" to "Bebidas", "sortOrder" to 2L))

        assertThat(category).isEqualTo(Category("c1", "Bebidas", 2))
    }

    @Test
    fun aCategoryWithoutANameIsIgnoredAndAMissingSortOrderDefaultsToZero() {
        assertThat(CatalogDocuments.categoryFrom("c2", mapOf("sortOrder" to 1L))).isNull()
        assertThat(CatalogDocuments.categoryFrom("c3", mapOf("name" to "Postres"))).isEqualTo(Category("c3", "Postres", 0))
    }

    @Test
    fun categoryDataCarriesExactlyTheRuleAllowedFields() {
        val data = CatalogDocuments.categoryData(Category("", "Bebidas", 2))

        assertThat(data).containsExactly("name", "Bebidas", "sortOrder", 2)
    }

    @Test
    fun aProductDocumentMapsPriceInCentavosAndPhotoVersion() {
        val product = CatalogDocuments.productFrom("p1", productDocument)

        assertThat(product).isEqualTo(
            Product("p1", "cat-platos", "Nacatamal", "Cerdo, arroz y papa", Money(12000), true, 2),
        )
    }

    @Test
    fun anInvalidProductDocumentIsIgnored() {
        assertThat(CatalogDocuments.productFrom("p2", productDocument - "name")).isNull()
        assertThat(CatalogDocuments.productFrom("p3", productDocument - "categoryId")).isNull()
        assertThat(CatalogDocuments.productFrom("p4", productDocument + ("priceCents" to -5L))).isNull()
    }

    @Test
    fun aProductWithoutAPhotoVersionOrAvailabilityDefaultsToNoPhotoAndUnavailable() {
        val product = checkNotNull(
            CatalogDocuments.productFrom("p5", productDocument - "photoVersion" - "isAvailable"),
        )

        assertThat(product.photoVersion).isEqualTo(0)
        assertThat(product.isAvailable).isFalse()
    }

    @Test
    fun productDataCarriesEveryRuleRequiredFieldWithThePriceAsIntegerCentavos() {
        val product = Product("", "cat-1", "Fresco", "Vaso", Money(2500), true, 0)

        val data = CatalogDocuments.productData(product, photoVersion = 1)

        assertThat(data.keys).containsExactly(
            "categoryId", "name", "description", "priceCents", "isAvailable", "photoVersion", "updatedAt",
        )
        assertThat(data["priceCents"]).isEqualTo(2500L)
        assertThat(data["photoVersion"]).isEqualTo(1)
        assertThat(data["categoryId"]).isEqualTo("cat-1")
    }

    @Test
    fun photoDataCarriesTheBytesAndTheVersion() {
        val data = CatalogDocuments.photoData(byteArrayOf(9, 8, 7), version = 4)

        assertThat(data.keys).containsExactly("jpeg", "version")
        assertThat(data["version"]).isEqualTo(4)
    }

    @Test
    fun nextPhotoVersionOnlyBumpsWhenAPhotoIsSupplied() {
        assertThat(CatalogDocuments.nextPhotoVersion(current = 3, hasNewPhoto = true)).isEqualTo(4)
        assertThat(CatalogDocuments.nextPhotoVersion(current = 3, hasNewPhoto = false)).isEqualTo(3)
        assertThat(CatalogDocuments.nextPhotoVersion(current = 0, hasNewPhoto = true)).isEqualTo(1)
    }

    @Test
    fun photoBytesAreReadBackFromTheStoredBlob() {
        val data = CatalogDocuments.photoData(byteArrayOf(9, 8, 7), version = 4)

        assertThat(CatalogDocuments.photoBytesFrom(data)).isEqualTo(byteArrayOf(9, 8, 7))
        assertThat(CatalogDocuments.photoBytesFrom(CatalogDocuments.photoData(byteArrayOf(1), 1))).isEqualTo(byteArrayOf(1))
    }

    @Test
    fun aPhotoDocumentWithoutUsableBytesYieldsNull() {
        assertThat(CatalogDocuments.photoBytesFrom(null)).isNull()
        assertThat(CatalogDocuments.photoBytesFrom(mapOf("version" to 2))).isNull()
        assertThat(CatalogDocuments.photoBytesFrom(mapOf("jpeg" to "not-a-blob"))).isNull()
    }
}
