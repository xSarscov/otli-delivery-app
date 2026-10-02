package com.otli.app.tracking.domain

import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus

/**
 * Who sees the courier's live position of an order, and when: the order's own customer and Admin, while
 * the order is `claimed` or `picked_up`. This mirrors the `liveLocations` read rule so the app never even
 * asks for a position it must not show; the rules stay the final authority (ADR-8).
 */
object LiveMapAccess {
    fun canView(order: Order, viewer: UserAccount?): Boolean {
        if (viewer == null || viewer.status != AccountStatus.ACTIVE) return false
        if (order.status != OrderStatus.CLAIMED && order.status != OrderStatus.PICKED_UP) return false
        return when (viewer.role) {
            Role.CUSTOMER -> order.customerId == viewer.uid
            Role.ADMIN -> true
            Role.MERCHANT, Role.COURIER -> false
        }
    }
}
