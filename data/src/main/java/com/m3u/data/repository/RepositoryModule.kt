@file:Suppress("unused")

package com.m3u.data.repository

import com.m3u.data.repository.media.MediaRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.repository.stream.StreamRepository
import com.m3u.data.repository.television.TelevisionRepository
import com.m3u.data.repository.media.MediaRepositoryImpl
import com.m3u.data.repository.playlist.PlaylistRepositoryImpl
import com.m3u.data.repository.programme.ProgrammeRepositoryImpl
import com.m3u.data.repository.stream.StreamRepositoryImpl
import com.m3u.data.repository.television.TelevisionRepositoryImpl
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
