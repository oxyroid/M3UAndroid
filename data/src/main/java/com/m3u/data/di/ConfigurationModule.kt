@file:Suppress("unused")

package com.m3u.data.di

import com.m3u.core.architecture.Configuration
import com.m3u.data.local.SharedConfiguration
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface ConfigurationModule {
    @Binds
    @Singleton
    fun bindConfiguration(
        configuration: SharedConfiguration
    ): Configuration
}