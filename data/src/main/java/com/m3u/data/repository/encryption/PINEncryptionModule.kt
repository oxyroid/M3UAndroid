package com.m3u.data.repository.encryption

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface PINEncryptionModule {
    @Binds
    @Singleton
    fun bindPINEncryptionRepository(
        impl: PINEncryptionRepositoryImpl
    ): PINEncryptionRepository
}
