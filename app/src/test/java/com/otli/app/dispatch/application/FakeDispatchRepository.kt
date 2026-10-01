package com.otli.app.dispatch.application

import com.otli.app.core.money.Money
import com.otli.app.dispatch.domain.ClaimDecision
import com.otli.app.dispatch.domain.CourierAvailability
import com.otli.app.dispatch.domain.PoolOrder
import com.otli.app.ordering.domain.OrderLocation
import com.otli.app.ordering.domain.Totals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

fun aPoolOrder(
    id: String = "o1",
    merchantName: String = "Comedor Marta",
    readyAtMillis: Long = 1_000L,
) = PoolOrder(
    id = id,
    merchantName = merchantName,
    pickup = OrderLocation(12.2667, -86.5667, "Frente al parque"),
    dropoff = OrderLocation(12.27, -86.57, "Casa azul"),
    totals = Totals(subtotal = Money(24000), fee = Money(3000), total = Money(27000)),
    readyAtMillis = readyAtMillis,
)

/**
 * In-memory courier side: [pool] and [courier] are the live documents every observer reads, like
 * Firestore listeners. A non-null [listenerError] makes every observer fail when collected. A
 * successful [setOnline] updates [courier] like the server would.
 */
class FakeDispatchRepository : DispatchRepository {
    val pool = MutableStateFlow<List<PoolOrder>>(emptyList())
    private val courierSource = MutableSharedFlow<CourierAvailability?>(replay = 1).also { it.tryEmit(null) }

    /** The courier document; every assignment is delivered to the observers, even an unchanged one, like a snapshot. */
    var courier: CourierAvailability? = null
        set(value) {
            field = value
            courierSource.tryEmit(value)
        }

    /** How many times the pool listener was started. */
    var poolSubscriptions = 0
        private set
    var listenerError: Throwable? = null

    /** While set, observers hold their first emission until it completes: the window before a listener delivers. */
    var firstEmissionGate: CompletableDeferred<Unit>? = null

    val claims = mutableListOf<Pair<String, String>>()
    var claimOutcome: suspend (orderId: String, courierId: String) -> Result<ClaimDecision> =
        { _, _ -> Result.success(ClaimDecision.Allowed) }

    val pickedUp = mutableListOf<Pair<String, String>>()
    var pickUpOutcome: suspend (orderId: String, courierId: String) -> Result<Unit> = { _, _ -> Result.success(Unit) }

    val delivered = mutableListOf<Pair<String, String>>()
    var deliverOutcome: suspend (orderId: String, courierId: String) -> Result<Unit> = { _, _ -> Result.success(Unit) }

    val onlineRequests = mutableListOf<Pair<String, Boolean>>()
    var onlineOutcome: suspend (courierId: String, online: Boolean) -> Result<Unit> = { _, _ -> Result.success(Unit) }

    override fun observePool(): Flow<List<PoolOrder>> = live(pool).onStart { poolSubscriptions++ }

    override fun observeCourier(courierId: String): Flow<CourierAvailability?> = live(courierSource)

    override suspend fun claim(orderId: String, courierId: String): Result<ClaimDecision> {
        claims += orderId to courierId
        return claimOutcome(orderId, courierId)
    }

    override suspend fun markPickedUp(orderId: String, courierId: String): Result<Unit> {
        pickedUp += orderId to courierId
        return pickUpOutcome(orderId, courierId)
    }

    override suspend fun markDelivered(orderId: String, courierId: String): Result<Unit> {
        delivered += orderId to courierId
        return deliverOutcome(orderId, courierId)
    }

    override suspend fun setOnline(courierId: String, online: Boolean): Result<Unit> {
        onlineRequests += courierId to online
        val result = onlineOutcome(courierId, online)
        if (result.isSuccess) courier = (courier ?: CourierAvailability(false, null)).copy(isOnline = online)
        return result
    }

    private fun <T> live(source: Flow<T>): Flow<T> = flow {
        listenerError?.let { throw it }
        firstEmissionGate?.await()
        emitAll(source)
    }
}
