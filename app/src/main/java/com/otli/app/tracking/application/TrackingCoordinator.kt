package com.otli.app.tracking.application

import com.otli.app.auth.application.AuthRepository
import com.otli.app.dispatch.application.DispatchRepository
import com.otli.app.ordering.application.OrderRepository
import com.otli.app.tracking.domain.TrackingPlan
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Decides, from the signed-in courier's availability, the status of the order in their slot and the
 * location permission and the device location switch, when the tracking service runs (ADR-8): it starts when
 * the courier is online with a `claimed`/`picked_up` order and stops as soon as the slot clears, the order
 * leaves those states, the permission goes, or the courier signs out. With the location services off the
 * service stays up so publishing resumes the moment they come back. It only observes and calls [TrackingController],
 * so it is testable without Android; the service is thin glue around [TrackingPublisher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackingCoordinator @Inject constructor(
    private val dispatch: DispatchRepository,
    private val orders: OrderRepository,
    private val auth: AuthRepository,
    private val controller: TrackingController,
) {
    /** The live plan; a failing listener (the rules deny reads after sign-out) ends in [TrackingPlan.Idle]. */
    fun plans(permitted: Flow<Boolean>, servicesOn: Flow<Boolean>): Flow<TrackingPlan> =
        auth.observeAuthState()
            .map { it?.uid }
            .distinctUntilChanged()
            .flatMapLatest { uid ->
                if (uid == null) {
                    flowOf(TrackingPlan.Idle)
                } else {
                    combine(serving(uid), permitted, servicesOn) { (courier, status), allowed, on -> TrackingPlan.of(uid, courier, status, allowed, on) }
                        .catch { emit(TrackingPlan.Idle) }
                }
            }
            .distinctUntilChanged()

    /** Starts the service for a [TrackingPlan.Share] or [TrackingPlan.ServicesOff] and stops it for anything else. */
    fun apply(plan: TrackingPlan) {
        when (plan) {
            is TrackingPlan.Share -> controller.start(plan.orderId, plan.courierId)
            is TrackingPlan.ServicesOff -> controller.start(plan.orderId, plan.courierId)
            is TrackingPlan.NeedsPermission, TrackingPlan.Idle -> controller.stop()
        }
    }

    suspend fun run(permitted: Flow<Boolean>, servicesOn: Flow<Boolean>) = plans(permitted, servicesOn).collect(::apply)

    /** The courier document and the status of the order its slot names; an unchanged document does not restart the order listener. */
    private fun serving(uid: String) = dispatch.observeCourier(uid)
        .distinctUntilChanged()
        .flatMapLatest { courier ->
            val orderId = courier?.activeOrderId
            if (orderId == null) flowOf(courier to null) else orders.observe(orderId).map { courier to it?.status }
        }
}
