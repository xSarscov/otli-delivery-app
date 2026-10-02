package com.otli.app.tracking.application

import com.otli.app.tracking.domain.GeoFix
import com.otli.app.tracking.domain.LivePosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

data class Published(val orderId: String, val courierId: String, val fix: GeoFix)

/**
 * In-memory `liveLocations`: [published] records every publish attempt in order and [outcome] decides
 * whether it succeeds; [position] is the live document every observer reads, like a Firestore listener.
 * A non-null [listenerError] makes every observer fail when collected.
 */
class FakeLocationRepository : LocationRepository {
    val published = mutableListOf<Published>()
    var outcome: suspend (Published) -> Result<Unit> = { Result.success(Unit) }

    val position = MutableStateFlow<LivePosition?>(null)
    var listenerError: Throwable? = null

    /** How many times [observe] listeners were started. */
    var observeStarts = 0
        private set

    override suspend fun publish(orderId: String, courierId: String, fix: GeoFix): Result<Unit> {
        val attempt = Published(orderId, courierId, fix)
        published += attempt
        return outcome(attempt)
    }

    override fun observe(orderId: String): Flow<LivePosition?> = flow {
        observeStarts++
        listenerError?.let { throw it }
        emitAll(position)
    }
}

/** A device position source the test pushes fixes into; [failure] makes collecting it fail, like a revoked permission. */
class FakeLocationSource : LocationSource {
    private val shared = MutableSharedFlow<GeoFix>(extraBufferCapacity = 64)
    var failure: Throwable? = null

    /** How many collectors are listening to the device right now. */
    val listeners: Int get() = shared.subscriptionCount.value

    fun emit(fix: GeoFix) = check(shared.tryEmit(fix)) { "no listener took the fix" }

    override fun fixes(): Flow<GeoFix> = flow {
        failure?.let { throw it }
        emitAll(shared)
    }
}
