package com.otli.app.ordering.adapters.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.otli.app.R
import com.otli.app.catalog.domain.PriceInput
import com.otli.app.core.notification.NotificationPort
import com.otli.app.core.notification.OrderNotice
import com.otli.app.ordering.adapters.ui.OrderStatusLabels
import com.otli.app.ordering.domain.OrderStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Shows order notices as system notifications on one channel (ADR-12: local only, no FCM).
 * One notification per order: a later status replaces the earlier one. When the user denied the
 * permission or silenced notifications the notice is dropped; nothing here can fail the caller.
 */
class AndroidNotificationPort @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationPort {
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)

    override fun notify(notice: OrderNotice) {
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()
        val (title, text) = when (notice) {
            is OrderNotice.NewOrder ->
                context.getString(R.string.notice_new_order_title) to
                    context.getString(R.string.notice_new_order_text, notice.customerName, PriceInput.format(notice.total))
            is OrderNotice.StatusChanged -> notice.merchantName to statusText(notice)
        }
        val built = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        manager.notify(notice.orderId.hashCode(), built)
    }

    private fun statusText(notice: OrderNotice.StatusChanged): String {
        val reason = notice.reason
        return if (notice.status == OrderStatus.REJECTED && !reason.isNullOrBlank()) {
            context.getString(R.string.notice_rejected_with_reason, reason)
        } else {
            context.getString(OrderStatusLabels.of(notice.status))
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notice_channel_name), NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)
    }

    /** Tapping a notice brings the app forward; null when the package has no launcher activity. */
    private fun openApp(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    companion object {
        const val CHANNEL_ID = "orders"
    }
}
