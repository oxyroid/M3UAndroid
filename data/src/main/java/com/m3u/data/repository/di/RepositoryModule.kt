@file:Suppress("unused")

package com.m3u.data.repository.di

import com.m3u.data.repository.MediaRepository
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.ProgrammeRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.data.repository.TelevisionRepository
import com.m3u.data.repository.internal.MediaRepositoryImpl
import com.m3u.data.repository.internal.PlaylistRepositoryImpl
import com.m3u.data.repository.internal.ProgrammeRepositoryImpl
import com.m3u.data.repository.internal.StreamRepositoryImpl
import com.m3u.data.repository.internal.TelevisionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface RepositoryModule {
    @Binds
    @Singleton
    fun bindPlaylistRepository(
        repository: PlaylistRepositoryImpl
    ): PlaylistRepository

    @Binds
    @Singleton
    fun bindStreamRepository(
        repository: StreamRepositoryImpl
    ): StreamRepository

    @Binds
    @Singleton
    fun bindProgrammeRepository(
        repositoryImpl: ProgrammeRepositoryImpl
    ): ProgrammeRepository

    @Binds
    @Singleton
    fun bindMediaRepository(
        repository: MediaRepositoryImpl
    ): MediaRepository

    @Binds
    @Singleton
    fun bindTelevisionRepository(
        repository: TelevisionRepositoryImpl
    ): TelevisionRepository
}
