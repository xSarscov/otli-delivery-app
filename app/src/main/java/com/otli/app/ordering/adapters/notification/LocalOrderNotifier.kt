package com.otli.app.ordering.adapters.notification

import com.otli.app.core.notification.NotificationPort
import com.otli.app.core.notification.OrderNotice
import com.otli.app.ordering.application.OrderRepository
import com.otli.app.ordering.domain.Order
import com.otli.app.ordering.domain.OrderStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.catch

/**
 * Turns the live order lists into local notices (ADR-12). The first list a watch sees is only the
 * baseline, so orders that already exist when the app starts never notify. After that a notice is
 * raised once per distinct status change, never for an unrelated field update.
 *
 * Each watch suspends while the listener is alive and returns normally when it fails (a listener
 * error such as PERMISSION_DENIED after sign-out must not crash the app).
 */
class LocalOrderNotifier @Inject constructor(
    private val repository: OrderRepository,
    private val port: NotificationPort,
) {
    /** Tells a customer about every status change of their orders. */
    suspend fun watchCustomer(customerId: String) {
        val lastStatus = mutableMapOf<String, OrderStatus>()
        var baseline = true
        repository.observeForCustomer(customerId)
            .catch { }
            .collect { orders ->
                if (!baseline) {
                    // An order unseen so far has just been placed, so PLACED is its starting status.
                    orders.filter { it.status != (lastStatus[it.id] ?: OrderStatus.PLACED) }.forEach { port.notify(statusNotice(it)) }
                }
                baseline = false
                orders.forEach { lastStatus[it.id] = it.status }
            }
    }

    /** Tells a merchant about each new order that arrives while they are watching. */
    suspend fun watchMerchant(merchantId: String) {
        val seen = mutableSetOf<String>()
        var baseline = true
        repository.observeForMerchant(merchantId)
            .catch { }
            .collect { orders ->
                if (!baseline) {
                    orders.filter { it.status == OrderStatus.PLACED && it.id !in seen }
                        .forEach { port.notify(OrderNotice.NewOrder(it.id, it.customerName, it.totals.total)) }
                }
                baseline = false
                seen += orders.map { it.id }
            }
    }

    private fun statusNotice(order: Order) =
        OrderNotice.StatusChanged(order.id, order.merchantName, order.status, order.rejectReason)
}
