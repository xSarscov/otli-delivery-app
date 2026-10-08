package com.otli.app.admin.di

import com.otli.app.admin.adapters.firestore.FirestoreAdminRepository
import com.otli.app.admin.application.AdminRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AdminModule {
    @Binds
    @Singleton
    abstract fun bindAdminRepository(impl: FirestoreAdminRepository): AdminRepository
}
