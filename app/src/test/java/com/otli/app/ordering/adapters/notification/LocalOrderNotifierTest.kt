package com.otli.app.ordering.adapters.notification

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.money.Money
import com.otli.app.core.notification.NotificationPort
import com.otli.app.core.notification.OrderNotice
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class RecordingNotificationPort : NotificationPort {
    val notices = mutableListOf<OrderNotice>()

    override fun notify(notice: OrderNotice) {
        notices += notice
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LocalOrderNotifierTest {
    private val repository = FakeOrderRepository()
    private val port = RecordingNotificationPort()
    private val notifier = LocalOrderNotifier(repository, port)

    // --- Customer: every status change of one of their orders, once ---

    @Test
    fun aCustomerIsNotToldAboutOrdersThatAlreadyExistWhenTheAppStarts() = runTest(UnconfinedTestDispatcher()) {
        repository.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED), anOrder("o2", OrderStatus.PLACED))

        backgroundScope.launch { notifier.watchCustomer("customer-1") }

        assertThat(port.notices).isEmpty()
    }

    @Test
    fun aCustomerIsToldWhenTheirOrderIsAccepted() = runTest(UnconfinedTestDispatcher()) {
        repository.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        backgroundScope.launch { notifier.watchCustomer("customer-1") }

        repository.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED))

        assertThat(port.notices).containsExactly(OrderNotice.StatusChanged("o1", "Comedor Marta", OrderStatus.ACCEPTED, null))
    }

    @Test
    fun eachDistinctStatusChangeIsToldOnceInOrder() = runTest(UnconfinedTestDispatcher()) {
        repository.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        backgroundScope.launch { notifier.watchCustomer("customer-1") }

        repository.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED))
        repository.orders.value = listOf(anOrder("o1", OrderStatus.PREPARING))
        repository.orders.value = listOf(anOrder("o1", OrderStatus.READY))

        assertThat(port.notices.map { (it as OrderNotice.StatusChanged).status })
            .containsExactly(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY).inOrder()
    }

    @Test
    fun anUnrelatedFieldUpdateOrAnotherOrderOfTheSameListDoesNotNotifyAgain() = runTest(UnconfinedTestDispatcher()) {
        repository.orders.value = listOf(anOrder("o1", OrderStatus.PLACED), anOrder("o2", OrderStatus.ACCEPTED))
        backgroundScope.launch { notifier.watchCustomer("customer-1") }
        repository.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED), anOrder("o2", OrderStatus.ACCEPTED))
        assertThat(port.notices).hasSize(1)

        repository.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED).copy(courierId = "c9"), anOrder("o2", OrderStatus.ACCEPTED))
        repository.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED).copy(customerPhone = "+50588880202"), anOrder("o2", OrderStatus.ACCEPTED))

        assertThat(port.notices).hasSize(1)
    }

    @Test
    fun aRejectionCarriesTheReason() = runTest(UnconfinedTestDispatcher()) {
        repository.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        backgroundScope.launch { notifier.watchCustomer("customer-1") }

        repository.orders.value = listOf(anOrder("o1", OrderStatus.REJECTED, rejectReason = "No hay nacatamales"))

        assertThat(port.notices)
            .containsExactly(OrderNotice.StatusChanged("o1", "Comedor Marta", OrderStatus.REJECTED, "No hay nacatamales"))
    }

    @Test
    fun aCustomerIsNotToldAboutTheOrderTheyJustPlacedButIsToldAboutALaterStatus() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { notifier.watchCustomer("customer-1") }
        repository.orders.value = emptyList()

        repository.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        assertThat(port.notices).isEmpty()

        repository.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED))
        assertThat(port.notices).hasSize(1)
    }

    @Test
    fun aCustomerIsOnlyToldAboutTheirOwnOrders() = runTest(UnconfinedTestDispatcher()) {
        repository.orders.value = listOf(anOrder("o1", OrderStatus.PLACED, customerId = "customer-2"))
        backgroundScope.launch { notifier.watchCustomer("customer-1") }

        repository.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED, customerId = "customer-2"))

        assertThat(port.notices).isEmpty()
    }

    @Test
    fun aFailingListenerEndsTheWatchWithoutCrashingOrNotifying() = runTest(UnconfinedTestDispatcher()) {
        repository.listenerError = IllegalStateException("PERMISSION_DENIED")

        notifier.watchCustomer("customer-1")
        notifier.watchMerchant("m1")

        assertThat(port.notices).isEmpty()
    }

    // --- Merchant: new orders addressed to them ---

    @Test
    fun aMerchantIsNotToldAboutOrdersAlreadyWaitingWhenTheAppStarts() = runTest(UnconfinedTestDispatcher()) {
        repository.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))

        backgroundScope.launch { notifier.watchMerchant("m1") }

        assertThat(port.notices).isEmpty()
    }

    @Test
    fun aMerchantIsToldOnceAboutANewPlacedOrder() = runTest(UnconfinedTestDispatcher()) {
        repository.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        backgroundScope.launch { notifier.watchMerchant("m1") }

        repository.orders.value = listOf(anOrder("o2", OrderStatus.PLACED, createdAtMillis = 2_000L), anOrder("o1", OrderStatus.PLACED))
        repository.orders.value = listOf(anOrder("o2", OrderStatus.PLACED, createdAtMillis = 2_000L).copy(courierId = "c1"), anOrder("o1", OrderStatus.PLACED))

        assertThat(port.notices).containsExactly(OrderNotice.NewOrder("o2", "Ana Lopez", Money(29500)))
    }

    @Test
    fun severalNewOrdersInOneUpdateAreEachToldAbout() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { notifier.watchMerchant("m1") }
        repository.orders.value = emptyList()

        repository.orders.value = listOf(anOrder("o2", OrderStatus.PLACED), anOrder("o3", OrderStatus.PLACED))

        assertThat(port.notices.map { it.orderId }).containsExactly("o2", "o3")
    }

    @Test
    fun aMerchantIsNotToldAboutAnOrderThatFirstShowsUpAlreadyAnswered() = runTest(UnconfinedTestDispatcher()) {
        backgroundScope.launch { notifier.watchMerchant("m1") }

        repository.orders.value = listOf(anOrder("o2", OrderStatus.ACCEPTED))

        assertThat(port.notices).isEmpty()
    }

    @Test
    fun aMerchantIsNotToldWhenTheyProgressAnOrderOrForAnotherStore()= runTest(UnconfinedTestDispatcher()) {
        repository.orders.value = listOf(anOrder("o1", OrderStatus.PLACED))
        backgroundScope.launch { notifier.watchMerchant("m1") }

        repository.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED))
        repository.orders.value = listOf(anOrder("o1", OrderStatus.ACCEPTED), anOrder("o9", OrderStatus.PLACED, merchantId = "m2"))

        assertThat(port.notices).isEmpty()
    }
}
