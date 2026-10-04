package com.otli.app.tracking.application

import com.google.common.truth.Truth.assertThat
import com.otli.app.ordering.application.FakeOrderRepository
import com.otli.app.ordering.application.anOrder
import com.otli.app.ordering.domain.OrderStatus
import com.otli.app.tracking.domain.GeoFix
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The device position is asked for only while the location services are on, and asked for again every time
 * they come back: a request made while they were off never delivers, which left the courier's position
 * unpublished after turning the GPS on from the warning (F.12).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServicesAwareLocationSourceTest {
    private val settings = FakeLocationSettingsChecker(initiallyOn = true)
    private val device = FakeDeviceLocationSource(settings)
    private val source = ServicesAwareLocationSource(device, settings)

    private val origin = GeoFix(12.2656, -86.5664, accuracyMeters = 6f, timestampMillis = 1_000_000L)
    private fun fix(seconds: Long) = origin.copy(timestampMillis = origin.timestampMillis + seconds * 1000)

    private fun TestScope.collecting(into: MutableList<GeoFix>): Job =
        launch(UnconfinedTestDispatcher(testScheduler)) { source.fixes().toList(into) }

    @Test
    fun withTheServicesOnTheFixesOfTheDeviceFlowThrough() = runTest {
        val received = mutableListOf<GeoFix>()
        val job = collecting(received)

        device.emit(origin)
        device.emit(fix(5))

        assertThat(received).containsExactly(origin, fix(5)).inOrder()
        job.cancel()
    }

    @Test
    fun withTheServicesOffTheDeviceIsNotAskedUntilTheyComeOn() = runTest {
        settings.enabled.value = false
        val received = mutableListOf<GeoFix>()
        val job = collecting(received)
        assertThat(device.requests).isEqualTo(0)

        settings.enabled.value = true
        device.emit(origin)

        assertThat(device.requests).isEqualTo(1)
        assertThat(received).containsExactly(origin)
        job.cancel()
    }

    @Test
    fun everyOffToOnTransitionAsksTheDeviceAnewAndOnToOffStopsListening() = runTest {
        val received = mutableListOf<GeoFix>()
        val job = collecting(received)
        device.emit(origin)
        assertThat(device.listeners).isEqualTo(1)

        settings.enabled.value = false
        assertThat(device.listeners).isEqualTo(0)
        device.emit(fix(5))

        settings.enabled.value = true
        assertThat(device.requests).isEqualTo(2)
        assertThat(device.listeners).isEqualTo(1)
        device.emit(fix(10))

        assertThat(received).containsExactly(origin, fix(10)).inOrder()
        job.cancel()
    }

    @Test
    fun aSwitchThatCannotBeReadIsAssumedOn() = runTest {
        settings.failure = IllegalStateException("no location manager")
        val received = mutableListOf<GeoFix>()
        val job = collecting(received)

        device.emit(origin)

        assertThat(received).containsExactly(origin)
        job.cancel()
    }

    @Test
    fun aRepeatedOnReportDoesNotRestartTheDeviceRequest() = runTest {
        val repeating = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
        val chatty = ServicesAwareLocationSource(
            device,
            object : LocationSettingsChecker {
                override fun servicesEnabled(): Flow<Boolean> = repeating
            },
        )
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { chatty.fixes().collect { } }

        repeating.emit(true)
        repeating.emit(true)

        assertThat(device.requests).isEqualTo(1)
        job.cancel()
    }

    @Test
    fun aDeviceThatFailsStillFailsTheCollector() = runTest {
        val broken = ServicesAwareLocationSource(
            object : LocationSource {
                override fun fixes() = FakeLocationSource().apply { failure = SecurityException("no permission") }.fixes()
            },
            settings,
        )

        val outcome = runCatching { broken.fixes().toList() }

        assertThat(outcome.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
    }

    // --- the delivery sequences, through the real publisher ---

    private val locations = FakeLocationRepository()
    private val orders = FakeOrderRepository()
    private val publisher = TrackingPublisher(source, locations, orders)

    private fun TestScope.publishing(): Job {
        orders.orders.value = listOf(anOrder("o1", OrderStatus.CLAIMED, courierId = "courier-1"))
        return launch(UnconfinedTestDispatcher(testScheduler)) { publisher.run("o1", "courier-1") }
    }

    @Test
    fun aDeliveryClaimedWithTheServicesOffPublishesAsSoonAsTheyAreTurnedOn() = runTest {
        settings.enabled.value = false
        val job = publishing()

        settings.enabled.value = true
        device.emit(origin)

        assertThat(locations.published).containsExactly(Published("o1", "courier-1", origin))
        job.cancel()
    }

    @Test
    fun togglingTheServicesOffAndOnDuringADeliveryKeepsPublishing() = runTest {
        val job = publishing()
        device.emit(origin)

        settings.enabled.value = false
        settings.enabled.value = true
        device.emit(fix(60))

        assertThat(locations.published.map { it.fix.timestampMillis })
            .containsExactly(origin.timestampMillis, fix(60).timestampMillis).inOrder()
        assertThat(job.isActive).isTrue()
        job.cancel()
    }
}
