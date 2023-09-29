@file:Suppress("unused")

package com.m3u.androidApp.di

import com.m3u.androidApp.AppPublisher
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
    @Publisher.App
    fun bindPublisher(provider: AppPublisher): Publisher
}
