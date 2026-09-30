package com.otli.app.catalog.adapters.firestore

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.Blob
import com.otli.app.auth.adapters.firestore.FirestoreAuthRepository
import com.otli.app.auth.domain.AccountStatus
import com.otli.app.core.di.OtliFirebase
import com.otli.app.core.firebase.await
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adapter proof against the emulators. Needs the seed data (`npm --prefix backend run seed`, run by
 * `test:android`): it signs in as the seeded active merchant `seed-merchant-1`.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreMerchantRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val auth = OtliFirebase.auth(context)
    private val firestore = OtliFirebase.firestore(context)
    private val repository = FirestoreMerchantRepository(firestore)

    private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(30_000) { block() } }

    @Before
    fun signInAsSeedMerchant() = await {
        auth.signOut()
        FirestoreAuthRepository(auth, firestore).login("merchant1@otli.test", PASSWORD).getOrThrow()
    }

    @After
    fun restoreSeedProfileAndSignOut() = await {
        if (auth.currentUser != null) {
            repository.updateProfile(MERCHANT_1, "Comedor Doña Marta", "Comida típica nicaragüense", "8888-0101")
            repository.setOpen(MERCHANT_1, true)
        }
        auth.signOut()
    }

    @Test
    fun updateProfileIsReflectedInTheObservedMerchantAndKeepsStatusAndLocation() = await {
        repository.updateProfile(MERCHANT_1, "Marta Renovada", "Nueva descripcion", "+50588880199").getOrThrow()

        val merchant = repository.observeMerchant(MERCHANT_1).first { it?.name == "Marta Renovada" }!!
        assertThat(merchant.description).isEqualTo("Nueva descripcion")
        assertThat(merchant.phone).isEqualTo("+50588880199")
        assertThat(merchant.status).isEqualTo(AccountStatus.ACTIVE)
        assertThat(merchant.location).isNotNull()
    }

    @Test
    fun closingTheStoreIsObservedAndReopeningRestoresIt() = await {
        repository.setOpen(MERCHANT_1, false).getOrThrow()
        assertThat(repository.observeMerchant(MERCHANT_1).first { it?.isOpen == false }).isNotNull()

        repository.setOpen(MERCHANT_1, true).getOrThrow()
        assertThat(repository.observeMerchant(MERCHANT_1).first { it?.isOpen == true }).isNotNull()
    }

    @Test
    fun updatePhotoStoresTheBytesAtTheProfilePhotoAndBumpsPhotoVersionEachTime() = await {
        val before = repository.observeMerchant(MERCHANT_1).first()!!.photoVersion
        val jpeg = byteArrayOf(1, 2, 3, 4)

        repository.updatePhoto(MERCHANT_1, jpeg).getOrThrow()
        repository.updatePhoto(MERCHANT_1, jpeg).getOrThrow()

        val merchant = repository.observeMerchant(MERCHANT_1).first { it!!.photoVersion == before + 2 }!!
        assertThat(merchant.photoVersion).isEqualTo(before + 2)
        val stored = firestore.collection("merchants").document(MERCHANT_1)
            .collection("productPhotos").document("profile").get().await()
        assertThat(stored.getBlob("jpeg")).isEqualTo(Blob.fromBytes(jpeg))
        assertThat(stored.getLong("version")).isEqualTo((before + 2).toLong())
    }

    @Test
    fun aMerchantCannotEditAnotherMerchantsProfile() = await {
        val result = repository.updateProfile(MERCHANT_2, "Hijacked", "x", "+50588880000")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun merchantsListHoldsActiveMerchantsOrderedByNameWithoutThePendingOne() = await {
        val merchants = repository.observeMerchantsList().first { it.isNotEmpty() }

        val ids = merchants.map { it.id }
        assertThat(ids).containsAtLeast(MERCHANT_1, MERCHANT_2)
        assertThat(ids).doesNotContain("seed-merchant-pending")
        assertThat(merchants.map { it.name }).isInStrictOrder()
    }

    private companion object {
        const val MERCHANT_1 = "seed-merchant-1"
        const val MERCHANT_2 = "seed-merchant-2"
        const val PASSWORD = "otli-demo-123"
    }
}
