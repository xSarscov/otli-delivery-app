package com.otli.app.tracking.adapters.device

import android.app.Application
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The device location switch (not the app permission) as a flow: what is on right now, then every change. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidLocationSettingsCheckerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val checker = AndroidLocationSettingsChecker(context)

    @Test
    fun itStartsWithTheCurrentStateWhenLocationIsOn() = runTest(UnconfinedTestDispatcher()) {
        shadowOf(locationManager).setLocationEnabled(true)
        val seen = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { checker.servicesEnabled().toList(seen) }

        assertThat(seen).containsExactly(true)
        job.cancel()
    }

    @Test
    fun itStartsWithTheCurrentStateWhenLocationIsOff() = runTest(UnconfinedTestDispatcher()) {
        shadowOf(locationManager).setLocationEnabled(false)
        val seen = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { checker.servicesEnabled().toList(seen) }

        assertThat(seen).containsExactly(false)
        job.cancel()
    }

    @Test
    fun itFollowsTheSwitchWhileCollected() = runTest(UnconfinedTestDispatcher()) {
        shadowOf(locationManager).setLocationEnabled(true)
        val seen = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { checker.servicesEnabled().toList(seen) }

        shadowOf(locationManager).setLocationEnabled(false)
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(locationManager).setLocationEnabled(true)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(seen).containsExactly(true, false, true).inOrder()
        job.cancel()
    }

    @Test
    fun itStopsListeningOnceCollectionEnds() = runTest(UnconfinedTestDispatcher()) {
        shadowOf(locationManager).setLocationEnabled(true)
        val seen = mutableListOf<Boolean>()
        val before = shadowOf(context).registeredReceivers.size
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { checker.servicesEnabled().toList(seen) }
        assertThat(shadowOf(context).registeredReceivers.size).isEqualTo(before + 1)
        job.cancel()

        shadowOf(locationManager).setLocationEnabled(false)
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(shadowOf(context).registeredReceivers.size).isEqualTo(before)
        assertThat(seen).containsExactly(true)
    }

    @Test
    fun aBroadcastThatChangesNothingIsNotReported() = runTest(UnconfinedTestDispatcher()) {
        shadowOf(locationManager).setLocationEnabled(true)
        val seen = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { checker.servicesEnabled().toList(seen) }

        context.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))
        context.sendBroadcast(Intent(LocationManager.MODE_CHANGED_ACTION))
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(seen).containsExactly(true)
        job.cancel()
    }
}
