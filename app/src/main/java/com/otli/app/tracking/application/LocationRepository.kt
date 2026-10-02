package com.otli.app.tracking.application

import com.otli.app.tracking.domain.GeoFix
import com.otli.app.tracking.domain.LivePosition
import kotlinx.coroutines.flow.Flow

/**
 * Port over `liveLocations/{orderId}`: the assigned courier overwrites one document per order while the
 * order is `claimed` or `picked_up`; the order's customer, its courier and Admin read it. The Firestore
 * rules stay the final authority (ADR-8): they refuse a write for any other state or less than 5 s after
 * the previous one, and that refusal comes back as a failed [Result].
 */
interface LocationRepository {
    /** Publishes [fix] as the position of [orderId]. The identity of the write is the signed-in user. */
    suspend fun publish(orderId: String, courierId: String, fix: GeoFix): Result<Unit>

    /**
     * The live position of [orderId], null while nothing was published. A failing listener (the viewer is
     * not allowed to read it) ends the flow with an error, so collectors must `catch`.
     */
    fun observe(orderId: String): Flow<LivePosition?>
}
