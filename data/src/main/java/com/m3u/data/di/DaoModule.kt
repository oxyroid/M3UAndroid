package com.m3u.data.di

import com.m3u.data.local.M3UDatabase
import com.m3u.data.local.dao.FeedDao
import com.m3u.data.local.dao.LiveDao
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
    fun provideFeedDao(
        database: M3UDatabase
    ): FeedDao = database.feedDao()
}