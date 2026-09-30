package com.otli.app.catalog.adapters.firestore

import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.catalog.domain.Merchant
import com.otli.app.catalog.domain.MerchantLocation
import org.junit.Test

class CatalogDocumentsTest {
    private val fullDocument: Map<String, Any?> = mapOf(
        "name" to "Comedor Marta",
        "description" to "Comida tipica",
        "phone" to "+50588880101",
        "status" to "active",
        "isOpen" to true,
        "photoVersion" to 3L,
        "location" to mapOf("lat" to 12.2667, "lng" to -86.5667, "reference" to "Frente al parque"),
    )

    @Test
    fun aFullMerchantDocumentMapsEveryField() {
        val merchant = CatalogDocuments.merchantFrom("m1", fullDocument)

        assertThat(merchant).isEqualTo(
            Merchant(
                id = "m1",
                name = "Comedor Marta",
                description = "Comida tipica",
                phone = "+50588880101",
                status = AccountStatus.ACTIVE,
                isOpen = true,
                photoVersion = 3,
                location = MerchantLocation(12.2667, -86.5667, "Frente al parque"),
            ),
        )
    }

    @Test
    fun aRegistrationTimeDocumentDefaultsToNoPhotoAndClosed() {
        val registration = mapOf(
            "name" to "Pulperia Test",
            "description" to "",
            "phone" to "+50588880000",
            "status" to "pending",
            "isOpen" to false,
            "location" to mapOf("lat" to 12.0, "lng" to -86.0, "reference" to ""),
        )

        val merchant = checkNotNull(CatalogDocuments.merchantFrom("m2", registration))

        assertThat(merchant.photoVersion).isEqualTo(0)
        assertThat(merchant.status).isEqualTo(AccountStatus.PENDING)
        assertThat(merchant.isOpen).isFalse()
        assertThat(merchant.location).isEqualTo(MerchantLocation(12.0, -86.0, ""))
    }

    @Test
    fun aMissingLocationMapsToNullInsteadOfFailing() {
        val merchant = checkNotNull(CatalogDocuments.merchantFrom("m3", fullDocument - "location"))

        assertThat(merchant.location).isNull()
    }

    @Test
    fun wholeNumberCoordinatesReturnedAsLongsAreAccepted() {
        val document = fullDocument + ("location" to mapOf("lat" to 12L, "lng" to -86L, "reference" to "x"))

        val merchant = checkNotNull(CatalogDocuments.merchantFrom("m4", document))

        assertThat(merchant.location).isEqualTo(MerchantLocation(12.0, -86.0, "x"))
    }

    @Test
    fun anUnrecognizedStatusOrMissingNameIsNotAMerchant() {
        assertThat(CatalogDocuments.merchantFrom("m5", fullDocument + ("status" to "banned"))).isNull()
        assertThat(CatalogDocuments.merchantFrom("m6", fullDocument - "name")).isNull()
    }

    @Test
    fun profileUpdateCarriesOnlyTheEditableFields() {
        val update = CatalogDocuments.profileUpdate("Nuevo", "Desc", "+50588880102")

        assertThat(update.keys).containsExactly("name", "description", "phone", "updatedAt")
        assertThat(update["name"]).isEqualTo("Nuevo")
        assertThat(update["phone"]).isEqualTo("+50588880102")
    }
}
