package com.otli.app.tracking.di

import com.otli.app.tracking.adapters.device.AndroidLocationSettingsChecker
import com.otli.app.tracking.adapters.device.FusedLocationSource
import com.otli.app.tracking.adapters.firestore.FirestoreLocationRepository
import com.otli.app.tracking.adapters.service.ServiceTrackingController
import com.otli.app.tracking.application.LocationRepository
import com.otli.app.tracking.application.LocationSettingsChecker
import com.otli.app.tracking.application.LocationSource
import com.otli.app.tracking.application.TrackingController
import dagger.Binds
import dagger.Module
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
    abstract fun bindLocationSource(impl: FusedLocationSource): LocationSource

    @Binds
    @Singleton
    abstract fun bindLocationSettingsChecker(impl: AndroidLocationSettingsChecker): LocationSettingsChecker

    @Binds
    @Singleton
    abstract fun bindTrackingController(impl: ServiceTrackingController): TrackingController
}
