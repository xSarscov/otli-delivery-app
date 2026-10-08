package com.otli.app.admin.application

import com.otli.app.auth.domain.AccountStatus
import com.otli.app.auth.domain.Role
import com.otli.app.auth.domain.UserAccount
import com.otli.app.core.money.Money
import com.otli.app.ordering.domain.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

fun anAccount(
    uid: String = "m1",
    role: Role = Role.MERCHANT,
    status: AccountStatus = AccountStatus.PENDING,
    displayName: String = "Comedor Marta",
) = UserAccount(uid, role, status, displayName, "$uid@otli.test", "+50588880201")

/**
 * In-memory Admin side: every write is recorded and, unless [writeFailure] is set, succeeds; the
 * three collections are the live data every observer reads, like Firestore listeners. A non-null
 * [listenerError] makes every observer fail when collected.
 */
class FakeAdminRepository : AdminRepository {
    data class StatusChange(val uid: String, val role: Role, val status: AccountStatus)

    data class Cancellation(val orderId: String, val reason: String)

    val statusChanges = mutableListOf<StatusChange>()
    val fees = mutableListOf<Money>()
    val releases = mutableListOf<String>()
    val cancellations = mutableListOf<Cancellation>()

    /** When set, every write fails with it. */
    var writeFailure: Throwable? = null

    /** Lets a test hold every write open, to observe the state while it is in flight. */
    var beforeWrite: suspend () -> Unit = {}

    val stuckOrders = MutableStateFlow<List<Order>>(emptyList())
    val managedAccounts = MutableStateFlow<List<UserAccount>>(emptyList())
    val allOrders = MutableStateFlow<List<Order>>(emptyList())
    var listenerError: Throwable? = null

    private suspend fun write(record: () -> Unit): Result<Unit> {
        beforeWrite()
        writeFailure?.let { return Result.failure(it) }
        record()
        return Result.success(Unit)
    }

    override suspend fun setAccountStatus(uid: String, role: Role, status: AccountStatus) =
        write { statusChanges += StatusChange(uid, role, status) }

    override suspend fun setDeliveryFee(fee: Money) = write { fees += fee }

    override suspend fun releaseClaim(orderId: String) = write { releases += orderId }

    override suspend fun cancelOrder(orderId: String, reason: String) = write { cancellations += Cancellation(orderId, reason) }

    override fun observeStuckOrders(): Flow<List<Order>> = observing(stuckOrders)

    override fun observeManagedAccounts(): Flow<List<UserAccount>> = observing(managedAccounts)

    override fun observeAllOrders(): Flow<List<Order>> = observing(allOrders)

    private fun <T> observing(source: Flow<T>): Flow<T> = flow {
        listenerError?.let { throw it }
        emitAll(source)
    }
}
