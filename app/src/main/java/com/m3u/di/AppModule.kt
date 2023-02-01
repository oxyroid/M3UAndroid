@file:Suppress("unused")

package com.m3u.di

import com.m3u.AppPackageProvider
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
    fun bindBuildConfigProvider(provider: AppPackageProvider): PackageProvider
}