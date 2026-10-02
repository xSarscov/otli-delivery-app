package com.otli.app.tracking.application

import com.otli.app.ordering.application.OrderRepository
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.tracking.domain.GeoFix
import com.otli.app.tracking.domain.LocationThrottle
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * The publishing loop of the tracking service for one order: every device fix goes through
 * [LocationThrottle] and the accepted ones are written with [LocationRepository.publish]. The loop
 * ends by itself, so a service that outlives the app screen still stops, when the order leaves
 * `claimed`/`picked_up` (delivered, released), when its listener fails (the rules deny reads after
 * sign-out) or when the device position is no longer available (permission revoked).
 */
class TrackingPublisher @Inject constructor(
    private val source: LocationSource,
    private val locations: LocationRepository,
    private val orders: OrderRepository,
) {
    suspend fun run(orderId: String, courierId: String) {
        merge(publishing(orderId, courierId), orderFinished(orderId)).first()
    }

    private fun publishing(orderId: String, courierId: String): Flow<Unit> = flow {
        var last: GeoFix? = null
        var retryNotBefore = Long.MIN_VALUE
        source.fixes()
            // Only the latest fix matters while a write is in flight (a slow network must not queue them).
            .conflate()
            .catch { /* the device position is gone: end the loop */ }
            .collect { fix ->
                if (fix.timestampMillis < retryNotBefore || !LocationThrottle.shouldPublish(last, fix)) return@collect
                if (locations.publish(orderId, courierId, fix).isSuccess) {
                    last = fix
                } else {
                    // A refused or failed write is not retried for a throttle interval: no write storms.
                    retryNotBefore = fix.timestampMillis + LocationThrottle.MIN_INTERVAL_MILLIS
                }
            }
        emit(Unit)
    }

    /** Emits once the order is known and no longer being delivered, or when its listener fails; a missing document is ignored. */
    private fun orderFinished(orderId: String): Flow<Unit> = orders.observe(orderId)
        .filter { it != null && it.status != OrderStatus.CLAIMED && it.status != OrderStatus.PICKED_UP }
        .map { }
        .catch { emit(Unit) }
}
