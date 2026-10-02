package com.otli.app.tracking.adapters.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.otli.app.tracking.application.TrackingController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Starts and stops [DeliveryTrackingService]. The app calls [start] while it is visible, which is what
 * lets Android start a foreground service of the location type without background location access.
 * The service ignores a start for the order it already shares.
 */
class ServiceTrackingController @Inject constructor(
    @ApplicationContext private val context: Context,
) : TrackingController {

    override fun start(orderId: String, courierId: String) {
        val intent = Intent(context, DeliveryTrackingService::class.java)
            .putExtra(DeliveryTrackingService.EXTRA_ORDER_ID, orderId)
            .putExtra(DeliveryTrackingService.EXTRA_COURIER_ID, courierId)
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stop() {
        context.stopService(Intent(context, DeliveryTrackingService::class.java))
    }
}
