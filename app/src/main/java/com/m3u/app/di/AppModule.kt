@file:Suppress("unused")

package com.m3u.app.di

import com.m3u.app.AppPublisher
import com.m3u.core.annotation.AppPublisherImpl
import com.m3u.core.architecture.Publisher
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
    @AppPublisherImpl
    fun bindPublisher(provider: AppPublisher): Publisher
}