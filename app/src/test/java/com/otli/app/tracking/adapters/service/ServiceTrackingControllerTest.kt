package com.otli.app.tracking.adapters.service

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ServiceTrackingControllerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val controller = ServiceTrackingController(context)

    @Test
    fun startingAsksTheSystemToStartTheForegroundServiceForTheOrderAndTheCourier() {
        controller.start("o1", "courier-1")

        val intent = shadowOf(context).nextStartedService
        assertThat(intent.component?.className).isEqualTo(DeliveryTrackingService::class.java.name)
        assertThat(intent.getStringExtra(DeliveryTrackingService.EXTRA_ORDER_ID)).isEqualTo("o1")
        assertThat(intent.getStringExtra(DeliveryTrackingService.EXTRA_COURIER_ID)).isEqualTo("courier-1")
    }

    @Test
    fun theIntentCarriesTheOrderItWasStartedFor() {
        controller.start("o2", "courier-9")

        val intent = shadowOf(context).nextStartedService
        assertThat(intent.getStringExtra(DeliveryTrackingService.EXTRA_ORDER_ID)).isEqualTo("o2")
        assertThat(intent.getStringExtra(DeliveryTrackingService.EXTRA_COURIER_ID)).isEqualTo("courier-9")
    }

    @Test
    fun stoppingStopsThatService() {
        controller.stop()

        val intent = shadowOf(context).nextStoppedService
        assertThat(intent.component?.className).isEqualTo(DeliveryTrackingService::class.java.name)
    }

    @Test
    fun stoppingNeverStartsAnything() {
        controller.stop()

        assertThat(shadowOf(context).nextStartedService).isNull()
    }
}
