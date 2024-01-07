@file:Suppress("unused")

package com.m3u.data.di

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.m3u.data.service.PlayerService
import com.m3u.data.service.DynamicMessageService
import com.m3u.data.service.impl.PlayerServiceImpl
import com.m3u.data.service.impl.DynamicMessageServiceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface BindServicesModule {
    @Binds
    @Singleton
    fun bindPlayerService(service: PlayerServiceImpl): PlayerService

    @Binds
    @Singleton
    fun bindUiServiceService(service: DynamicMessageServiceImpl): DynamicMessageService
}

@Module
@InstallIn(SingletonComponent::class)
object ProvidedServicesModule {
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

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
