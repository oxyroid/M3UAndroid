@file:Suppress("unused")

package com.m3u.data.service.di

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.m3u.core.architecture.service.BannerService
import com.m3u.core.architecture.service.NotificationService
import com.m3u.core.architecture.service.PlayerManager
import com.m3u.data.service.AndroidNotificationService
import com.m3u.data.service.AndroidPlayerManager
import com.m3u.data.service.ConflatedBannerService
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
    fun bindPlayerService(service: AndroidPlayerManager): PlayerManager

    @Binds
    @Singleton
    fun bindNotificationService(service: AndroidNotificationService): NotificationService

    @Binds
    @Singleton
    fun bindConflatedBannerService(service: ConflatedBannerService): BannerService
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