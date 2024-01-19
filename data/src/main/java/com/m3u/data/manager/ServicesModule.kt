@file:Suppress("unused")

package com.m3u.data.manager

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.m3u.core.architecture.TraceFileProvider
import com.m3u.core.architecture.logger.Logger
import com.m3u.data.manager.impl.TraceFileProviderImpl
import com.m3u.data.repository.logger.CommonLogger
import com.m3u.data.repository.logger.UiLogger
import com.m3u.data.manager.impl.PlayerManagerImpl
import com.m3u.data.manager.impl.MessageManagerImpl
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
    fun bindPlayerService(service: PlayerManagerImpl): PlayerManager

    @Binds
    @Singleton
    fun bindUiServiceService(service: MessageManagerImpl): MessageManager

    @Binds
    @Singleton
    fun bindTraceFileProvider(provider: TraceFileProviderImpl): TraceFileProvider

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
