@file:Suppress("unused")

package com.m3u.app.di

import com.m3u.app.AppPackageProvider
import com.m3u.core.architecture.PackageProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppModule {
    @Binds
    @Singleton
    fun bindPackageProvider(provider: AppPackageProvider): PackageProvider
}