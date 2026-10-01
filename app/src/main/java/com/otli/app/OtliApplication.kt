package com.otli.app

import android.app.Application
import com.otli.app.ordering.adapters.notification.OrderNotificationCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class OtliApplication : Application() {
    @Inject lateinit var orderNotifications: OrderNotificationCoordinator

    override fun onCreate() {
        super.onCreate()
        // Local notices (ADR-12) work while the process lives, so the watch is tied to it, not to a screen.
        orderNotifications.start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
