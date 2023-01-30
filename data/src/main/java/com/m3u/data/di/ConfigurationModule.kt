@file:Suppress("unused")

package com.m3u.data.di

import com.m3u.data.Configuration
import com.m3u.data.SharedConfiguration
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