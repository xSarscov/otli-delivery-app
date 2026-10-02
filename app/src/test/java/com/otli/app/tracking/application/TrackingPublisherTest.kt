package com.otli.app.tracking.application

import com.google.common.truth.Truth.assertThat
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.tracking.domain.GeoFix
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingPublisherTest {
    private val source = FakeLocationSource()
    private val locations = FakeLocationRepository()
    private val orders = FakeOrderRepository()
    private val publisher = TrackingPublisher(source, locations, orders)

    private val origin = GeoFix(12.2656, -86.5664, accuracyMeters = 6f, timestampMillis = 1_000_000L)

    /** A fix [seconds] after [origin], [metersNorth] metres up the road (1 degree of latitude is 111.2 km). */
    private fun fix(seconds: Long, metersNorth: Double = 0.0) = origin.copy(
        latitude = origin.latitude + metersNorth / 111_195.0,
        timestampMillis = origin.timestampMillis + seconds * 1000,
    )

    private fun serving(status: OrderStatus = OrderStatus.CLAIMED) {
        orders.orders.value = listOf(anOrder("o1", status, courierId = "courier-1"))
    }

    private fun TestScope.running(): Job = launch(UnconfinedTestDispatcher(testScheduler)) { publisher.run("o1", "courier-1") }

    private fun runPublishing(block: suspend TestScope.(job: Job) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        serving()
        val job = running()
        block(job)
        job.cancel()
    }

    @Test
    fun theFirstFixIsPublishedForTheOrderAndTheCourier() = runPublishing {
        source.emit(origin)

        assertThat(locations.published).containsExactly(Published("o1", "courier-1", origin))
    }

    @Test
    fun fixesCloserThanTenSecondsOrTenMetersAreHeldBack() = runPublishing {
        source.emit(origin)

        source.emit(fix(seconds = 5, metersNorth = 400.0))
        source.emit(fix(seconds = 30, metersNorth = 3.0))

        assertThat(locations.published).hasSize(1)
    }

    @Test
    fun aFixAfterTenSecondsAndTenMetersIsPublishedAndBecomesTheNewReference() = runPublishing {
        source.emit(origin)

        source.emit(fix(seconds = 12, metersNorth = 25.0))
        source.emit(fix(seconds = 14, metersNorth = 60.0))

        assertThat(locations.published.map { it.fix }).containsExactly(origin, fix(12, 25.0)).inOrder()
    }

    @Test
    fun aStationaryCourierPublishesTheHeartbeatEverySixtySeconds() = runPublishing {
        source.emit(origin)

        source.emit(fix(seconds = 59))
        source.emit(fix(seconds = 60))
        source.emit(fix(seconds = 100))
        source.emit(fix(seconds = 120))

        assertThat(locations.published.map { it.fix.timestampMillis })
            .containsExactly(origin.timestampMillis, fix(60).timestampMillis, fix(120).timestampMillis).inOrder()
    }

    @Test
    fun aRejectedPublishDoesNotMoveTheReferenceButIsNotRetriedBeforeTenSeconds() = runPublishing {
        locations.outcome = { Result.failure(IllegalStateException("denied")) }
        source.emit(origin)
        locations.outcome = { Result.success(Unit) }

        source.emit(fix(seconds = 4, metersNorth = 50.0))
        source.emit(fix(seconds = 9, metersNorth = 50.0))
        source.emit(fix(seconds = 11, metersNorth = 50.0))

        assertThat(locations.published.map { it.fix.timestampMillis })
            .containsExactly(origin.timestampMillis, fix(11).timestampMillis).inOrder()
    }

    @Test
    fun afterASuccessTheRejectedAttemptDoesNotBlockTheNextRegularPublish() = runPublishing {
        source.emit(origin)
        locations.outcome = { Result.failure(IllegalStateException("offline")) }
        source.emit(fix(seconds = 15, metersNorth = 30.0))
        locations.outcome = { Result.success(Unit) }

        source.emit(fix(seconds = 26, metersNorth = 30.0))

        assertThat(locations.published.map { it.fix.timestampMillis })
            .containsExactly(origin.timestampMillis, fix(15).timestampMillis, fix(26).timestampMillis).inOrder()
    }

    @Test
    fun whileAWriteIsInFlightOnlyTheLatestFixWaitsForIt() = runPublishing {
        val gate = CompletableDeferred<Unit>()
        locations.outcome = { gate.await(); Result.success(Unit) }
        source.emit(origin)

        source.emit(fix(seconds = 12, metersNorth = 30.0))
        source.emit(fix(seconds = 24, metersNorth = 60.0))
        gate.complete(Unit)

        assertThat(locations.published.map { it.fix.timestampMillis })
            .containsExactly(origin.timestampMillis, fix(24).timestampMillis).inOrder()
    }

    @Test
    fun theSharingContinuesWhenTheOrderIsPickedUp() = runPublishing { job ->
        source.emit(origin)

        serving(OrderStatus.PICKED_UP)
        source.emit(fix(seconds = 12, metersNorth = 30.0))

        assertThat(job.isActive).isTrue()
        assertThat(locations.published).hasSize(2)
    }

    @Test
    fun itStopsPublishingAndStopsListeningWhenTheOrderIsDelivered() = runPublishing { job ->
        source.emit(origin)
        assertThat(source.listeners).isEqualTo(1)

        serving(OrderStatus.DELIVERED)

        assertThat(job.isCompleted).isTrue()
        assertThat(source.listeners).isEqualTo(0)
        assertThat(locations.published).hasSize(1)
    }

    @Test
    fun itStopsWhenTheOrderIsReleasedBackToReady() = runPublishing { job ->
        serving(OrderStatus.READY)

        assertThat(job.isCompleted).isTrue()
        assertThat(source.listeners).isEqualTo(0)
    }

    @Test
    fun itKeepsRunningWhileTheOrderDocumentIsNotThereYet() = runTest(UnconfinedTestDispatcher()) {
        val job = running()

        source.emit(origin)

        assertThat(job.isActive).isTrue()
        assertThat(locations.published).hasSize(1)
        job.cancel()
    }

    @Test
    fun itStopsWhenTheOrderListenerFails() = runTest(UnconfinedTestDispatcher()) {
        orders.listenerError = IllegalStateException("permission denied")

        val job = running()

        assertThat(job.isCompleted).isTrue()
        assertThat(source.listeners).isEqualTo(0)
    }

    @Test
    fun itStopsWhenTheDevicePositionIsNotAvailable() = runTest(UnconfinedTestDispatcher()) {
        serving()
        source.failure = SecurityException("no location permission")

        val job = running()

        assertThat(job.isCompleted).isTrue()
        assertThat(locations.published).isEmpty()
    }
}
