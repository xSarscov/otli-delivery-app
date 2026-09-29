package com.otli.app.auth.di

import com.otli.app.auth.adapters.firestore.FirestoreAuthRepository
import com.otli.app.auth.application.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirestoreAuthRepository): AuthRepository
}
