package com.otli.app.tracking.adapters.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.otli.app.tracking.application.TrackingPublisher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service of the location type (ADR-8) that keeps publishing the courier's position for one
 * order while the app is in the background. All decisions live in [TrackingPublisher] (which also ends
 * when the order stops being delivered) and in the coordinator that starts and stops this service; this
 * class only owns the notification and the coroutine. It is not sticky: if the system kills it, the
 * coordinator starts it again the next time the courier opens the app.
 */
@AndroidEntryPoint
class DeliveryTrackingService : Service() {
    @Inject lateinit var publisher: TrackingPublisher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var sharing: Job? = null
    private var sharedOrderId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val orderId = intent?.getStringExtra(EXTRA_ORDER_ID)
        val courierId = intent?.getStringExtra(EXTRA_COURIER_ID)
        if (orderId == null || courierId == null || !enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (sharing?.isActive != true || sharedOrderId != orderId) {
            sharing?.cancel()
            sharedOrderId = orderId
            sharing = scope.launch {
                try {
                    publisher.run(orderId, courierId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    // Nothing to recover here: the service ends and the next app start re-evaluates the plan.
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** False when the system refuses a location foreground service (permission revoked, app not visible). */
    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, TrackingNotification.create(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        true
    } catch (refused: RuntimeException) {
        false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ORDER_ID = "orderId"
        const val EXTRA_COURIER_ID = "courierId"
        private const val NOTIFICATION_ID = 7001
    }
}
