package com.otli.app.dispatch.di

import com.otli.app.dispatch.adapters.firestore.FirestoreDispatchRepository
import com.otli.app.dispatch.application.DispatchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DispatchModule {
    @Binds
    @Singleton
    abstract fun bindDispatchRepository(impl: FirestoreDispatchRepository): DispatchRepository
}
