package com.otli.app.ordering.adapters.notification

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.otli.app.R
import com.otli.app.core.money.Money
import com.otli.app.core.notification.OrderNotice
import com.otli.app.ordering.adapters.ui.OrderStatusLabels
import com.otli.app.ordering.domain.OrderStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidNotificationPortTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val port = AndroidNotificationPort(context)

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun aNewOrderNamesTheCustomerAndTheTotal() {
        port.notify(OrderNotice.NewOrder("o1", "Ana Lopez", Money(29500)))

        val notification = posted().single()
        assertThat(notification.title()).isEqualTo(context.getString(R.string.notice_new_order_title))
        assertThat(notification.text()).contains("Ana Lopez")
        assertThat(notification.text()).contains("295")
    }

    @Test
    fun aStatusChangeNamesTheStoreAndTheNewStatus() {
        port.notify(OrderNotice.StatusChanged("o1", "Comedor Marta", OrderStatus.PREPARING, null))

        val notification = posted().single()
        assertThat(notification.title()).isEqualTo("Comedor Marta")
        assertThat(notification.text()).isEqualTo(context.getString(R.string.order_status_preparing))
    }

    @Test
    fun aRejectionCarriesTheMerchantsReason() {
        port.notify(OrderNotice.StatusChanged("o1", "Comedor Marta", OrderStatus.REJECTED, "No hay nacatamales"))

        assertThat(posted().single().text()).isEqualTo(context.getString(R.string.notice_rejected_with_reason, "No hay nacatamales"))
    }

    @Test
    fun aRejectionWithoutAReasonShowsJustTheStatus() {
        port.notify(OrderNotice.StatusChanged("o1", "Comedor Marta", OrderStatus.REJECTED, null))

        assertThat(posted().single().text()).isEqualTo(context.getString(R.string.order_status_rejected))
    }

    @Test
    fun aReasonOnlyMattersForARejection() {
        port.notify(OrderNotice.StatusChanged("o1", "Comedor Marta", OrderStatus.ACCEPTED, "stale reason"))

        assertThat(posted().single().text()).isEqualTo(context.getString(R.string.order_status_accepted))
    }

    @Test
    fun anotherStatusOfTheSameOrderReplacesItsNotificationWhileAnotherOrderAddsOne() {
        port.notify(OrderNotice.StatusChanged("o1", "Comedor Marta", OrderStatus.ACCEPTED, null))
        port.notify(OrderNotice.StatusChanged("o1", "Comedor Marta", OrderStatus.PREPARING, null))
        assertThat(posted()).hasSize(1)
        assertThat(posted().single().text()).isEqualTo(context.getString(R.string.order_status_preparing))

        port.notify(OrderNotice.StatusChanged("o2", "Comedor Marta", OrderStatus.ACCEPTED, null))
        assertThat(posted()).hasSize(2)
    }

    @Test
    fun noticesGoToAHighImportanceChannelTheUserCanSilence() {
        port.notify(OrderNotice.NewOrder("o1", "Ana Lopez", Money(29500)))

        val channel = manager.getNotificationChannel(AndroidNotificationPort.CHANNEL_ID)
        assertThat(channel.name.toString()).isEqualTo(context.getString(R.string.notice_channel_name))
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(posted().single().channelId).isEqualTo(AndroidNotificationPort.CHANNEL_ID)
    }

    @Test
    fun aNoticeTheUserDeniedIsDroppedWithoutFailing() {
        shadowOf(manager).setNotificationsEnabled(false)

        port.notify(OrderNotice.NewOrder("o1", "Ana Lopez", Money(29500)))

        assertThat(posted()).isEmpty()
    }

    @Test
    fun everyStatusHasItsOwnLabel() {
        val labels = OrderStatus.entries.map { context.getString(OrderStatusLabels.of(it)) }

        assertThat(labels.toSet()).hasSize(OrderStatus.entries.size)
        assertThat(labels.all { it.isNotBlank() }).isTrue()
    }
}
