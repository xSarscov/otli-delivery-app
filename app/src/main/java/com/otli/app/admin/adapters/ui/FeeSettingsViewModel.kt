package com.otli.app.admin.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.admin.application.AdminActionResult
import com.otli.app.admin.application.UpdateFee
import com.otli.app.catalog.domain.PriceInput
import com.otli.app.core.money.Money
import com.otli.app.ordering.application.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the last attempt to save the fee ended; shown under the field until the text is edited. */
enum class FeeSaveResult { SAVED, INVALID_AMOUNT, NOT_POSITIVE, SAVE_FAILED }

data class FeeSettingsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** The fee orders snapshot right now, as last read or saved. */
    val currentFee: Money? = null,
    /** What Admin typed, in C$ ("30" or "30.50"). */
    val input: String = "",
    val isSaving: Boolean = false,
    val result: FeeSaveResult? = null,
) {
    val canSave: Boolean get() = !isLoading && !isSaving && input.isNotBlank()
}

/** The flat city delivery fee: reads the current value and saves a new one. Existing orders keep their snapshot. */
@HiltViewModel
class FeeSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val updateFee: UpdateFee,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeeSettingsUiState())
    val uiState: StateFlow<FeeSettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        _uiState.value = FeeSettingsUiState()
        load()
    }

    fun onInputChange(text: String) = _uiState.update { it.copy(input = text, result = null) }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving) return
        val fee = PriceInput.parse(state.input)
        if (fee == null) {
            _uiState.update { it.copy(result = FeeSaveResult.INVALID_AMOUNT) }
            return
        }
        _uiState.update { it.copy(isSaving = true, result = null) }
        viewModelScope.launch {
            val outcome = updateFee(fee)
            _uiState.update { current ->
                when (outcome) {
                    AdminActionResult.Done ->
                        current.copy(isSaving = false, currentFee = fee, input = PriceInput.format(fee), result = FeeSaveResult.SAVED)
                    // The only refusal UpdateFee makes is a fee that is not positive.
                    is AdminActionResult.Rejected -> current.copy(isSaving = false, result = FeeSaveResult.NOT_POSITIVE)
                    AdminActionResult.Failed -> current.copy(isSaving = false, result = FeeSaveResult.SAVE_FAILED)
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            settings.deliveryFee().fold(
                onSuccess = { fee -> _uiState.update { it.copy(isLoading = false, currentFee = fee, input = PriceInput.format(fee)) } },
                onFailure = { _uiState.update { it.copy(isLoading = false, loadFailed = true) } },
            )
        }
    }
}
