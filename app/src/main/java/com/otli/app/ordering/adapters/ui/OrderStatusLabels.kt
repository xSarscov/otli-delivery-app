package com.otli.app.ordering.adapters.ui

import androidx.annotation.StringRes
import com.otli.app.R
import com.otli.app.ordering.domain.OrderStatus

/** The user-facing wording of each order status, shared by the notices, the board and the tracking screen. */
object OrderStatusLabels {
    @StringRes
    fun of(status: OrderStatus): Int = when (status) {
        OrderStatus.PLACED -> R.string.order_status_placed
        OrderStatus.ACCEPTED -> R.string.order_status_accepted
        OrderStatus.PREPARING -> R.string.order_status_preparing
        OrderStatus.READY -> R.string.order_status_ready
        OrderStatus.CLAIMED -> R.string.order_status_claimed
        OrderStatus.PICKED_UP -> R.string.order_status_picked_up
        OrderStatus.DELIVERED -> R.string.order_status_delivered
        OrderStatus.REJECTED -> R.string.order_status_rejected
        OrderStatus.CANCELLED -> R.string.order_status_cancelled
    }
}
