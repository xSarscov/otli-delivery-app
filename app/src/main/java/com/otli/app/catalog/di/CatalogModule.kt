package com.otli.app.catalog.di

import com.otli.app.catalog.adapters.device.BitmapPhotoDecoder
import com.otli.app.catalog.adapters.device.ImageCompressor
import com.otli.app.catalog.adapters.firestore.FirestoreCatalogRepository
import com.otli.app.catalog.adapters.firestore.FirestoreMerchantRepository
import com.otli.app.catalog.adapters.firestore.FirestorePhotoSource
import com.otli.app.catalog.adapters.ui.PhotoDecoder
import com.otli.app.catalog.application.CatalogRepository
import com.otli.app.catalog.application.MerchantRepository
import com.otli.app.catalog.application.PhotoCompressor
import com.otli.app.catalog.application.PhotoSource
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

    @Binds
    abstract fun bindPhotoCompressor(impl: ImageCompressor): PhotoCompressor

    @Binds
    abstract fun bindPhotoDecoder(impl: BitmapPhotoDecoder): PhotoDecoder

    @Binds
    abstract fun bindPhotoSource(impl: FirestorePhotoSource): PhotoSource
}
