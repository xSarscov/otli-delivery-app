package com.otli.app.catalog.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.auth.adapters.ui.RecordingAuthRepository
import com.otli.app.auth.domain.AuthUser
import com.otli.app.core.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

class MerchantProfileViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val merchants = FakeMerchantRepository()
    private val compressor = FakePhotoCompressor()
    private val auth = RecordingAuthRepository().apply { authState.value = AuthUser("m1", "marta@otli.test") }

    private fun viewModel() = MerchantProfileViewModel(merchants, compressor, auth)

    @Test
    fun loadsTheSignedInMerchantsProfileIntoTheForm() {
        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.name).isEqualTo("Comedor Marta")
        assertThat(state.description).isEqualTo("Comida tipica")
        assertThat(state.phone).isEqualTo("+50588880101")
        assertThat(state.isOpen).isTrue()
    }

    @Test
    fun staysLoadingUntilThereIsASignedInUser() {
        auth.authState.value = null

        assertThat(viewModel().uiState.value.isLoading).isTrue()
    }

    @Test
    fun aMissingMerchantDocumentIsReportedInsteadOfLoadingForever() {
        merchants.merchant.value = null
        val state = viewModel().uiState.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.merchantMissing).isTrue()
    }

    @Test
    fun savingSendsTrimmedNameDescriptionAndNormalizedPhone() {
        val viewModel = viewModel()
        viewModel.onNameChange("  Marta Renovada ")
        viewModel.onDescriptionChange(" Nueva ")
        viewModel.onPhoneChange("8888-0199")
        viewModel.save()

        assertThat(merchants.profileUpdates).containsExactly(
            FakeMerchantRepository.ProfileUpdate("m1", "Marta Renovada", "Nueva", "+50588880199"),
        )
        assertThat(viewModel.uiState.value.justSaved).isTrue()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun aBlankNameIsRefusedWithoutWriting() {
        val viewModel = viewModel()
        viewModel.onNameChange("   ")
        viewModel.save()

        assertThat(viewModel.uiState.value.error).isEqualTo(MerchantProfileError.NAME_REQUIRED)
        assertThat(merchants.profileUpdates).isEmpty()
    }

    @Test
    fun anInvalidPhoneIsRefusedWithoutWriting() {
        val viewModel = viewModel()
        viewModel.onPhoneChange("12345")
        viewModel.save()

        assertThat(viewModel.uiState.value.error).isEqualTo(MerchantProfileError.INVALID_PHONE)
        assertThat(merchants.profileUpdates).isEmpty()
    }

    @Test
    fun aFailedSaveShowsAnErrorAndEditingClearsIt() {
        merchants.writeOutcome = { Result.failure(IllegalStateException("offline")) }
        val viewModel = viewModel()
        viewModel.save()

        assertThat(viewModel.uiState.value.error).isEqualTo(MerchantProfileError.SAVE_FAILED)
        assertThat(viewModel.uiState.value.justSaved).isFalse()
        assertThat(viewModel.uiState.value.isSaving).isFalse()

        viewModel.onNameChange("Otro")
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun aSecondSaveWhileOneIsInFlightIsIgnored() {
        val gate = CompletableDeferred<Result<Unit>>()
        merchants.writeOutcome = { gate.await() }
        val viewModel = viewModel()
        viewModel.save()

        assertThat(viewModel.uiState.value.isSaving).isTrue()
        viewModel.save()
        assertThat(merchants.profileUpdates).hasSize(1)

        gate.complete(Result.success(Unit))
        assertThat(viewModel.uiState.value.isSaving).isFalse()
    }

    @Test
    fun exposesTheMerchantIdSoTheProfilePhotoCanBeLoaded() {
        assertThat(viewModel().uiState.value.merchantId).isEqualTo("m1")

        merchants.merchant.value = aMerchant(id = "m2", photoVersion = 3)

        assertThat(viewModel().uiState.value.merchantId).isEqualTo("m2")
    }

    @Test
    fun aLaterRemoteChangeUpdatesTheOpenFlagButNeverOverwritesEdits() {
        val viewModel = viewModel()
        viewModel.onNameChange("Editando")

        merchants.merchant.value = aMerchant(name = "Nombre remoto", isOpen = false, photoVersion = 5)

        val state = viewModel.uiState.value
        assertThat(state.name).isEqualTo("Editando")
        assertThat(state.isOpen).isFalse()
        assertThat(state.photoVersion).isEqualTo(5)
    }

    @Test
    fun closingAndReopeningTheStoreWritesEachChoice() {
        val viewModel = viewModel()

        viewModel.onOpenChange(false)
        assertThat(viewModel.uiState.value.isOpen).isFalse()
        viewModel.onOpenChange(true)

        assertThat(merchants.openChanges).containsExactly(false, true).inOrder()
        assertThat(viewModel.uiState.value.isOpen).isTrue()
    }

    @Test
    fun aFailedOpenToggleShowsAnError() {
        merchants.writeOutcome = { Result.failure(IllegalStateException("denied")) }
        val viewModel = viewModel()

        viewModel.onOpenChange(false)

        assertThat(viewModel.uiState.value.error).isEqualTo(MerchantProfileError.TOGGLE_FAILED)
        assertThat(viewModel.uiState.value.isOpen).isTrue()
    }

    @Test
    fun aPickedPhotoIsCompressedBeforeItIsUploaded() {
        val viewModel = viewModel()

        viewModel.onPhotoPicked(byteArrayOf(1, 2, 3))

        assertThat(merchants.photos).hasSize(1)
        assertThat(merchants.photos.single()).isEqualTo(byteArrayOf(3, 2, 1))
        assertThat(viewModel.uiState.value.photoVersion).isEqualTo(1)
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun anUnreadablePhotoShowsAnErrorAndUploadsNothing() {
        compressor.outcome = { Result.failure(IllegalArgumentException("not an image")) }
        val viewModel = viewModel()

        viewModel.onPhotoPicked(byteArrayOf(9))

        assertThat(viewModel.uiState.value.error).isEqualTo(MerchantProfileError.PHOTO_FAILED)
        assertThat(merchants.photos).isEmpty()
    }

    @Test
    fun aFailedPhotoUploadShowsAnError() {
        merchants.writeOutcome = { Result.failure(IllegalStateException("offline")) }
        val viewModel = viewModel()

        viewModel.onPhotoPicked(byteArrayOf(1))

        assertThat(viewModel.uiState.value.error).isEqualTo(MerchantProfileError.PHOTO_FAILED)
        assertThat(viewModel.uiState.value.isUploadingPhoto).isFalse()
    }
}
