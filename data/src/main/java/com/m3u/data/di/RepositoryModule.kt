@file:Suppress("unused")

package com.m3u.data.di

import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import com.m3u.data.repository.MediaRepository
import com.m3u.data.repository.impl.FeedRepositoryImpl
import com.m3u.data.repository.impl.LiveRepositoryImpl
import com.m3u.data.repository.impl.MediaRepositoryImpl
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

    @Binds
    @Singleton
    fun bindMediaRepository(
        repository: MediaRepositoryImpl
    ): MediaRepository
}
