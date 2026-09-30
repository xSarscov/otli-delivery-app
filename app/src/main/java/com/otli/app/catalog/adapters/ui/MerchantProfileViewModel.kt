package com.otli.app.catalog.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import com.otli.app.auth.domain.NicaraguanPhone
import com.otli.app.catalog.application.MerchantRepository
import com.otli.app.catalog.application.PhotoCompressor
import com.otli.app.catalog.domain.CatalogValidation
import com.otli.app.catalog.domain.Merchant
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MerchantProfileError { NAME_REQUIRED, INVALID_PHONE, SAVE_FAILED, TOGGLE_FAILED, PHOTO_FAILED }

data class MerchantProfileUiState(
    val isLoading: Boolean = true,
    val merchantMissing: Boolean = false,
    val name: String = "",
    val description: String = "",
    val phone: String = "",
    val isOpen: Boolean = false,
    val photoVersion: Int = 0,
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val justSaved: Boolean = false,
    val error: MerchantProfileError? = null,
)

/**
 * Edits the signed-in merchant's own profile. The form fields are filled once from the first
 * snapshot so a live update never overwrites what the merchant is typing; the open flag and photo
 * version always follow the document.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MerchantProfileViewModel @Inject constructor(
    private val merchants: MerchantRepository,
    private val photos: PhotoCompressor,
    auth: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MerchantProfileUiState())
    val uiState: StateFlow<MerchantProfileUiState> = _uiState.asStateFlow()

    private var merchantId: String? = null
    private var formFilled = false

    init {
        viewModelScope.launch {
            auth.observeAuthState()
                .filterNotNull()
                .map { it.uid }
                .distinctUntilChanged()
                .flatMapLatest { uid ->
                    merchantId = uid
                    merchants.observeMerchant(uid)
                }
                .collect(::onMerchant)
        }
    }

    fun onNameChange(value: String) = edit { copy(name = value) }

    fun onDescriptionChange(value: String) = edit { copy(description = value) }

    fun onPhoneChange(value: String) = edit { copy(phone = value) }

    fun save() {
        val id = merchantId ?: return
        val form = _uiState.value
        if (form.isSaving) return
        val name = form.name.trim()
        val phone = NicaraguanPhone.normalize(form.phone)
        val invalid = when {
            CatalogValidation.validateName(name) != null -> MerchantProfileError.NAME_REQUIRED
            phone == null -> MerchantProfileError.INVALID_PHONE
            else -> null
        }
        if (invalid != null || phone == null) {
            _uiState.update { it.copy(error = invalid, justSaved = false) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null, justSaved = false) }
        viewModelScope.launch {
            val result = merchants.updateProfile(id, name, form.description.trim(), phone)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    justSaved = result.isSuccess,
                    error = if (result.isFailure) MerchantProfileError.SAVE_FAILED else null,
                )
            }
        }
    }

    fun onOpenChange(open: Boolean) {
        val id = merchantId ?: return
        viewModelScope.launch {
            val result = merchants.setOpen(id, open)
            if (result.isFailure) _uiState.update { it.copy(error = MerchantProfileError.TOGGLE_FAILED) }
        }
    }

    /** [picked] is the raw image the merchant chose; it is compressed here before it leaves the device. */
    fun onPhotoPicked(picked: ByteArray) {
        val id = merchantId ?: return
        _uiState.update { it.copy(isUploadingPhoto = true, error = null) }
        viewModelScope.launch {
            val result = photos.compress(picked).fold(
                onSuccess = { merchants.updatePhoto(id, it) },
                onFailure = { Result.failure(it) },
            )
            _uiState.update {
                it.copy(
                    isUploadingPhoto = false,
                    error = if (result.isFailure) MerchantProfileError.PHOTO_FAILED else null,
                )
            }
        }
    }

    private fun onMerchant(merchant: Merchant?) {
        if (merchant == null) {
            _uiState.update { it.copy(isLoading = false, merchantMissing = true) }
            return
        }
        _uiState.update { state ->
            val withLive = state.copy(
                isLoading = false,
                merchantMissing = false,
                isOpen = merchant.isOpen,
                photoVersion = merchant.photoVersion,
            )
            if (formFilled) {
                withLive
            } else {
                withLive.copy(name = merchant.name, description = merchant.description, phone = merchant.phone)
            }
        }
        formFilled = true
    }

    private fun edit(change: MerchantProfileUiState.() -> MerchantProfileUiState) =
        _uiState.update { it.change().copy(error = null, justSaved = false) }
}
