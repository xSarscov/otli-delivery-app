package com.otli.app.tracking.adapters.ui

import com.google.common.truth.Truth.assertThat
import com.otli.app.core.map.MapPin
import com.otli.app.core.testing.MainDispatcherRule
import com.otli.app.tracking.application.FakeLocationSource
import com.otli.app.tracking.domain.GeoFix
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The courier's own dot on the delivery map: the device fixes, only while they are wanted and permitted. */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnPositionViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val source = FakeLocationSource()
    // Created lazily: viewModelScope needs the test main dispatcher, which the rule installs after construction.
    private val viewModel by lazy { OwnPositionViewModel(source) }

    private fun fix(latitude: Double, longitude: Double) = GeoFix(latitude, longitude, accuracyMeters = 5f, timestampMillis = 1_000L)

    /** Keeps the position hot like the delivery screen does while it is visible. */
    private fun TestScope.watch() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.position.collect { } }
    }

    /** Lets the view model react: its flows run on the main test dispatcher. */
    private fun TestScope.want(isWanted: Boolean) {
        viewModel.setWanted(isWanted)
        testScheduler.runCurrent()
    }

    @Test
    fun withoutAnythingWantedThereIsNoPositionAndTheDeviceIsNotListened() = runTest {
        watch()

        assertThat(viewModel.position.value).isNull()
        assertThat(source.listeners).isEqualTo(0)
    }

    @Test
    fun itFollowsTheDeviceFixesWhileWanted() = runTest {
        watch()
        want(true)

        source.emit(fix(12.268, -86.568))
        assertThat(viewModel.position.value).isEqualTo(MapPin(12.268, -86.568))

        source.emit(fix(12.269, -86.569))
        assertThat(viewModel.position.value).isEqualTo(MapPin(12.269, -86.569))
    }

    @Test
    fun noLongerWantingItClearsTheDotAndStopsListening() = runTest {
        watch()
        want(true)
        source.emit(fix(12.268, -86.568))
        assertThat(source.listeners).isEqualTo(1)

        want(false)

        assertThat(viewModel.position.value).isNull()
        assertThat(source.listeners).isEqualTo(0)
    }

    @Test
    fun aFailingDeviceSourceDoesNotCrashAndLeavesNoDot() = runTest {
        source.failure = SecurityException("no location permission")
        watch()

        want(true)

        assertThat(viewModel.position.value).isNull()
    }

    @Test
    fun wantingItAgainAfterAFailureListensAnew() = runTest {
        source.failure = SecurityException("no location permission")
        watch()
        want(true)
        want(false)

        source.failure = null
        want(true)
        source.emit(fix(12.27, -86.57))

        assertThat(viewModel.position.value).isEqualTo(MapPin(12.27, -86.57))
    }

    @Test
    fun theDeviceIsNotListenedToWhileNobodyWatches() = runTest {
        want(true)

        assertThat(source.listeners).isEqualTo(0)
    }
}
