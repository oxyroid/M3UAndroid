@file:Suppress("unused")

package com.m3u.data.di

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.m3u.data.service.UiService
import com.m3u.data.service.NotificationService
import com.m3u.data.service.PlayerManager
import com.m3u.data.service.impl.NotificationServiceImpl
import com.m3u.data.service.impl.PlayerManagerImpl
import com.m3u.data.service.impl.UiServiceImpl
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
    fun bindPlayerService(service: PlayerManagerImpl): PlayerManager

    @Binds
    @Singleton
    fun bindNotificationService(service: NotificationServiceImpl): NotificationService

    @Binds
    @Singleton
    fun bindUiServiceService(service: UiServiceImpl): UiService
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