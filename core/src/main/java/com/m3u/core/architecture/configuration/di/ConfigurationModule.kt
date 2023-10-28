@file:Suppress("unused")

package com.m3u.core.architecture.configuration.di

import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.configuration.SharedConfiguration
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
