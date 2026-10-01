package com.otli.app.ordering.di

import com.otli.app.core.notification.NotificationPort
import com.otli.app.ordering.adapters.firestore.FirestoreOrderRepository
import com.otli.app.ordering.adapters.firestore.FirestoreSettingsRepository
import com.otli.app.ordering.adapters.notification.AndroidNotificationPort
import com.otli.app.ordering.application.OrderRepository
import com.otli.app.ordering.application.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OrderingModule {
    @Binds
    @Singleton
    abstract fun bindOrderRepository(impl: FirestoreOrderRepository): OrderRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: FirestoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindNotificationPort(impl: AndroidNotificationPort): NotificationPort
}
