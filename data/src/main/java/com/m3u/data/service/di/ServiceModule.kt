@file:Suppress("unused")

package com.m3u.data.service.di

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.m3u.core.architecture.service.DownloadService
import com.m3u.core.architecture.service.NotificationService
import com.m3u.core.architecture.service.PlayerManager
import com.m3u.data.service.ExoPlayerManager
import com.m3u.data.service.NotificationServiceImpl
import com.m3u.data.service.SystemDownloadService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface ServiceModule {
    @Binds
    @Singleton
    fun bindDownloadService(service: SystemDownloadService): DownloadService

    @Binds
    @Singleton
    fun bindPlayerService(service: ExoPlayerManager): PlayerManager

    @Binds
    @Singleton
    fun bindNotificationService(service: NotificationServiceImpl): NotificationService
}

@Module
@InstallIn(SingletonComponent::class)
object SystemServiceModule {
    @Provides
    @Singleton
    fun provideNotificationManagerCompat(@ApplicationContext context: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(context)
    }

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
}