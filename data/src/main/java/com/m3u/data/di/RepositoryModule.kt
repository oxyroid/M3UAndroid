@file:Suppress("unused")

package com.m3u.data.di

import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.impl.FeedRepositoryImpl
import com.m3u.data.repository.impl.LiveRepositoryImpl
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
    fun bindFeedRepository(
        repository: FeedRepositoryImpl
    ): FeedRepository

    @Binds
    @Singleton
    fun bindLiveRepository(
        repository: LiveRepositoryImpl
    ): LiveRepository
}