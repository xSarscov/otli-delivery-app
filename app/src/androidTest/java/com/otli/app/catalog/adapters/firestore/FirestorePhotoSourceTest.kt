package com.otli.app.catalog.adapters.firestore

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.adapters.firestore.FirestoreAuthRepository
import com.otli.app.catalog.application.PhotoKey
import com.otli.app.catalog.domain.Product
import com.otli.app.core.di.OtliFirebase
import com.otli.app.core.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Characterization test of [FirestorePhotoSource] against the emulators (needs the seed data that
 * `test:android` loads). The adapter already worked when this class was written, so no RED was
 * observable; its worth is checked with transient mutants of `fetch` instead (otli-device-evidence O2).
 */
@RunWith(AndroidJUnit4::class)
class FirestorePhotoSourceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val auth = OtliFirebase.auth(context)
    private val firestore = OtliFirebase.firestore(context)
    private val authRepository = FirestoreAuthRepository(auth, firestore)
    private val catalog = FirestoreCatalogRepository(firestore, FirestoreMerchantRepository(firestore))
    private val source = FirestorePhotoSource(firestore)
    private val created = mutableListOf<String>()

    // Returns Unit so every `@Test fun x() = await { ... }` compiles to a void JUnit method.
    private fun await(block: suspend () -> Unit): Unit = runBlocking { withTimeout(30_000) { block() } }

    private suspend fun signInAs(email: String) {
        auth.signOut()
        authRepository.login(email, PASSWORD).getOrThrow()
    }

    @Before
    fun signInAsSeedMerchant() = await { signInAs("merchant1@otli.test") }

    @After
    fun cleanUp() = await {
        if (created.isNotEmpty()) {
            signInAs("merchant1@otli.test")
            created.forEach { catalog.removeProduct(MERCHANT_1, it) }
        }
        auth.signOut()
    }

    @Test
    fun aCustomerReadsAMerchantsProductPhoto() = await {
        val jpeg = byteArrayOf(9, 8, 7, 6)
        catalog.upsertProduct(
            MERCHANT_1,
            Product("", "cat-platos", "Test Foto Cruzada", "desc", Money(1200), true, 0),
            jpeg,
        ).getOrThrow()
        val product = catalog.observeProducts(MERCHANT_1).first { list -> list.any { it.name == "Test Foto Cruzada" } }
            .first { it.name == "Test Foto Cruzada" }
        created += product.id

        signInAs("customer1@otli.test")

        assertThat(source.fetch(MERCHANT_1, product.id)).isEqualTo(jpeg)
    }

    @Test
    fun theProfilePhotoIsReadUnderTheFixedProfileId() = await {
        val jpeg = byteArrayOf(3, 1, 4, 1, 5)
        signInAs("merchant2@otli.test")
        FirestoreMerchantRepository(firestore).updatePhoto(MERCHANT_2, jpeg).getOrThrow()

        assertThat(source.fetch(MERCHANT_2, PhotoKey.PROFILE_PHOTO_ID)).isEqualTo(jpeg)
    }

    @Test
    fun aPhotoThatDoesNotExistIsNull() = await {
        assertThat(source.fetch(MERCHANT_2, "no-such-photo")).isNull()
    }

    @Test
    fun aSignedOutReaderIsRefused() = await {
        auth.signOut()

        val result = runCatching { source.fetch(MERCHANT_1, "no-such-photo") }

        assertThat(result.isFailure).isTrue()
    }

    private companion object {
        const val MERCHANT_1 = "seed-merchant-1"
        const val MERCHANT_2 = "seed-merchant-2"
        const val PASSWORD = "otli-demo-123"
    }
}
