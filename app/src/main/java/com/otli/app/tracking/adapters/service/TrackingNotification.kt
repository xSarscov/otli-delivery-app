package com.otli.app.tracking.adapters.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import com.otli.app.R

/**
 * The ongoing notification a foreground service must show: it tells the courier their location is
 * being shared for the delivery. Quiet (low importance) because it stays up for the whole delivery.
 */
internal object TrackingNotification {
    const val CHANNEL_ID = "tracking"

    fun create(context: Context): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.tracking_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(context.getString(R.string.tracking_notification_title))
            .setContentText(context.getString(R.string.tracking_notification_text))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openApp(context))
            .build()
    }

    /** Tapping it brings the app forward; null when the package has no launcher activity. */
    private fun openApp(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
}
