package com.otli.app.tracking.adapters.service

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackingNotificationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun theNotificationTellsTheCourierTheLocationIsBeingShared() {
        val notification = TrackingNotification.create(context)

        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo(context.getString(R.string.tracking_notification_title))
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo(context.getString(R.string.tracking_notification_text))
    }

    @Test
    fun itCannotBeSwipedAwayWhileTheDeliveryLasts() {
        val notification = TrackingNotification.create(context)

        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
    }

    @Test
    fun itUsesItsOwnQuietChannel() {
        val notification = TrackingNotification.create(context)

        assertThat(notification.channelId).isEqualTo(TrackingNotification.CHANNEL_ID)
        val channel = manager.getNotificationChannel(TrackingNotification.CHANNEL_ID)
        assertThat(channel.name.toString()).isEqualTo(context.getString(R.string.tracking_channel_name))
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }
}
