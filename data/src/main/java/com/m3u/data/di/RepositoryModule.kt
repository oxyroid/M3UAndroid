@file:Suppress("unused")
package com.m3u.data.di

import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.SubscriptionRepository
import com.m3u.data.repository.impl.LiveRepositoryImpl
import com.m3u.data.repository.impl.SubscriptionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    @Singleton
    fun bindSubscriptionRepository(
        subscriptionRepository: SubscriptionRepositoryImpl
    ): SubscriptionRepository

    @Binds
    @Singleton
    fun bindLiveRepository(
        liveRepository: LiveRepositoryImpl
    ): LiveRepository
}