package com.otli.app.tracking.di

import com.otli.app.tracking.adapters.device.AndroidLocationSettingsChecker
import com.otli.app.tracking.adapters.device.FusedLocationSource
import com.otli.app.tracking.adapters.firestore.FirestoreLocationRepository
import com.otli.app.tracking.adapters.service.ServiceTrackingController
import com.otli.app.tracking.application.LocationRepository
import com.otli.app.tracking.application.LocationSettingsChecker
import com.otli.app.tracking.application.LocationSource
import com.otli.app.tracking.application.ServicesAwareLocationSource
import com.otli.app.tracking.application.TrackingController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {
    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: FirestoreLocationRepository): LocationRepository

    @Binds
    @Singleton
    abstract fun bindLocationSettingsChecker(impl: AndroidLocationSettingsChecker): LocationSettingsChecker

    @Binds
    @Singleton
    abstract fun bindTrackingController(impl: ServiceTrackingController): TrackingController

    companion object {
        /** Both the tracking service and the courier's own dot read the device through the services switch (F.12). */
        @Provides
        @Singleton
        fun provideLocationSource(device: FusedLocationSource, settings: LocationSettingsChecker): LocationSource =
            ServicesAwareLocationSource(device, settings)
    }
}
