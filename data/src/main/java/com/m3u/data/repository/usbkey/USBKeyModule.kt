package com.m3u.data.repository.usbkey

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface USBKeyModule {
    @Binds
    @Singleton
    fun bindUSBKeyRepository(
        repository: USBKeyRepositoryImpl
    ): USBKeyRepository
}
