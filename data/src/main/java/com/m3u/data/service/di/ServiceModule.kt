@file:Suppress("unused")

package com.m3u.data.service.di

import com.m3u.data.service.DownloadService
import com.m3u.data.service.PlayerManager
import com.m3u.data.service.impl.ExoPlayerManager
import com.m3u.data.service.impl.SystemDownloadService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface ServiceModule {
    @Binds
    @Singleton
    fun bindDownloadService(service: SystemDownloadService): DownloadService

    @Binds
    fun bindPlayerService(service: ExoPlayerManager): PlayerManager
}