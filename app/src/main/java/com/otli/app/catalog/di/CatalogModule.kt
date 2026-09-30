package com.otli.app.catalog.di

import com.otli.app.catalog.adapters.firestore.FirestoreCatalogRepository
import com.otli.app.catalog.adapters.firestore.FirestoreMerchantRepository
import com.otli.app.catalog.application.CatalogRepository
import com.otli.app.catalog.application.MerchantRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogModule {
    @Binds
    @Singleton
    abstract fun bindMerchantRepository(impl: FirestoreMerchantRepository): MerchantRepository

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: FirestoreCatalogRepository): CatalogRepository
}
