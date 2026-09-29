package com.otli.app.auth.adapters.ui

import androidx.annotation.StringRes
import com.otli.app.R
import com.otli.app.auth.domain.Role

@StringRes
fun Role.labelRes(): Int = when (this) {
    Role.CUSTOMER -> R.string.role_customer
    Role.MERCHANT -> R.string.role_merchant
    Role.COURIER -> R.string.role_courier
    Role.ADMIN -> R.string.role_admin
}
