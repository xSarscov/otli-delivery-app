package com.otli.app.ordering.adapters.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otli.app.auth.application.AuthRepository
import com.otli.app.core.map.MapPin
import com.otli.app.core.money.Money
import com.otli.app.core.result.suspendRunCatching
import com.otli.app.ordering.application.CartStore
import com.otli.app.ordering.application.OrderCustomer
import com.otli.app.ordering.application.PlaceOrder
import com.otli.app.ordering.application.PlaceOrderRejection
import com.otli.app.ordering.application.PlaceOrderResult
import com.otli.app.ordering.application.SettingsRepository
import com.otli.app.ordering.domain.CheckoutCalculator
import com.otli.app.ordering.domain.OrderLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Collects the delivery pin, reference and cash confirmation and places the cart as an order.
 * The fee is re-read when the customer confirms: if it moved since the total was shown, the new
 * total is displayed and a second confirmation is required, so nobody pays a total they did not see.
 */
@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val cart: CartStore,
    private val placeOrder: PlaceOrder,
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            cart.state.collect { state ->
                _uiState.update {
                    it.copy(
                        merchantName = state.cart.merchant?.name,
                        subtotal = CheckoutCalculator.totals(state.cart.lines, Money(0)).subtotal,
                        isCartEmpty = state.cart.isEmpty,
                    )
                }
            }
        }
        viewModelScope.launch {
            settings.deliveryFee().fold(
                onSuccess = { fee -> _uiState.update { it.copy(fee = fee) } },
                onFailure = { _uiState.update { it.copy(error = CheckoutError.Rejected(PlaceOrderRejection.FeeUnavailable)) } },
            )
        }
    }

    fun setPin(pin: MapPin?) = edit { it.copy(pin = pin) }

    fun setReference(reference: String) = edit { it.copy(reference = reference) }

    fun setCashConfirmed(confirmed: Boolean) = edit { it.copy(cashConfirmed = confirmed) }

    fun place() {
        if (_uiState.value.isPlacing) return
        _uiState.update { it.copy(isPlacing = true, error = null) }
        viewModelScope.launch {
            val error = submit()
            _uiState.update { it.copy(isPlacing = false, error = error ?: it.error) }
        }
    }

    /** Runs the checks and the placement; returns why it stopped, or null once the order is placed. */
    private suspend fun submit(): CheckoutError? {
        val form = _uiState.value
        val current = cart.state.value.cart
        if (current.isEmpty) return CheckoutError.Rejected(PlaceOrderRejection.EmptyCart)
        val valid = when (val checked = CheckoutForm.validate(form.pin, form.reference, form.cashConfirmed)) {
            is CheckoutFormResult.Valid -> checked
            is CheckoutFormResult.Invalid -> return checked.error
        }

        val fee = settings.deliveryFee().getOrElse { return CheckoutError.Rejected(PlaceOrderRejection.FeeUnavailable) }
        if (fee != form.fee) {
            _uiState.update { it.copy(fee = fee) }
            return CheckoutError.FeeChanged(fee)
        }
        val customer = customer() ?: return CheckoutError.ProfileUnavailable

        return when (val result = placeOrder(customer, current, OrderLocation(valid.pin.latitude, valid.pin.longitude, valid.reference))) {
            is PlaceOrderResult.Placed -> {
                cart.clear()
                _uiState.update { it.copy(placedOrderId = result.orderId) }
                null
            }
            is PlaceOrderResult.Rejected -> CheckoutError.Rejected(result.reason)
        }
    }

    private suspend fun customer(): OrderCustomer? = suspendRunCatching {
        val uid = auth.observeAuthState().first()?.uid ?: return@suspendRunCatching null
        auth.observeUserDocument(uid).first()?.let { OrderCustomer(uid, it.displayName, it.phone) }
    }.getOrNull()

    /** A form edit also retires the message about the previous attempt. */
    private fun edit(change: (CheckoutUiState) -> CheckoutUiState) = _uiState.update { change(it).copy(error = null) }
}
