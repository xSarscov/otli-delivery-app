package com.otli.app.catalog.adapters.ui

import com.otli.app.auth.domain.AccountStatus
import com.otli.app.catalog.application.MerchantRepository
import com.otli.app.catalog.application.PhotoCompressor
import com.otli.app.catalog.domain.Merchant
import com.otli.app.catalog.domain.MerchantLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

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

    override fun observeMerchantsList(): Flow<List<Merchant>> = emptyFlow()
}

/** Returns the input reversed so tests can tell compressed bytes from picked bytes. */
class FakePhotoCompressor : PhotoCompressor {
    var outcome: (ByteArray) -> Result<ByteArray> = { Result.success(it.reversedArray()) }

    override fun compress(source: ByteArray): Result<ByteArray> = outcome(source)
}
