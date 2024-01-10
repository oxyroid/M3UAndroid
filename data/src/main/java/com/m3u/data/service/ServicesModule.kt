@file:Suppress("unused")

package com.m3u.data.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.m3u.core.architecture.FilePathCacher
import com.m3u.core.architecture.logger.Logger
import com.m3u.data.service.impl.CrashFilePathCacher
import com.m3u.data.repository.logger.CommonLogger
import com.m3u.data.repository.logger.UiLogger
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

    @Binds
    @Singleton
    fun bindFilePathCacher(cacher: CrashFilePathCacher): FilePathCacher

    @Binds
    @Singleton
    fun bindCommonLogger(logger: CommonLogger): Logger

    @Binds
    @Singleton
    @Logger.Ui
    fun bindUiLogger(logger: UiLogger): Logger
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
