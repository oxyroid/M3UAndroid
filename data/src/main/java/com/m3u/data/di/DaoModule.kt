package com.m3u.data.di

import com.m3u.data.M3UDatabase
import com.m3u.data.dao.LiveDao
import com.m3u.data.dao.SubscriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {
    @Provides
    fun provideLiveDao(
        database: M3UDatabase
    ): LiveDao = database.liveDao()

    @Provides
    fun provideSubscriptionDao(
        database: M3UDatabase
    ): SubscriptionDao = database.subscriptionDao()
}