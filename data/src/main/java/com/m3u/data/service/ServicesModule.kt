@file:Suppress("unused")

package com.m3u.data.service

import android.app.NotificationManager
import android.content.Context
import android.net.nsd.NsdManager
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.m3u.core.architecture.TraceFileProvider
import com.m3u.core.architecture.logger.Logger
import com.m3u.data.television.http.HttpServer
import com.m3u.data.television.http.internal.HttpServerImpl
import com.m3u.data.television.nsd.NsdDeviceManager
import com.m3u.data.television.nsd.internal.NsdDeviceManagerImpl
import com.m3u.data.service.internal.MessagerImpl
import com.m3u.data.service.internal.PlayerManagerImpl
import com.m3u.data.service.internal.TraceFileProviderImpl
import com.m3u.data.repository.logger.StubLogger
import com.m3u.data.repository.logger.MessageLogger
import com.m3u.data.service.internal.RemoteDirectionServiceImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface BindServicesModule {
    @Binds
    @Singleton
    fun bindPlayerManager(service: PlayerManagerImpl): PlayerManager

    @Binds
    @Singleton
    fun bindMessageManager(service: MessagerImpl): Messager

    @Binds
    @Singleton
    fun bindTraceFileProvider(provider: TraceFileProviderImpl): TraceFileProvider

    @Binds
    @Singleton
    fun bindCommonLogger(logger: StubLogger): Logger

    @Binds
    @Singleton
    @Logger.MessageImpl
    fun bindMessageLogger(logger: MessageLogger): Logger

    @Binds
    @Singleton
    fun bindNsdDeviceManager(manager: NsdDeviceManagerImpl): NsdDeviceManager

    @Binds
    @Singleton
    fun bindHttpServer(server: HttpServerImpl): HttpServer

    @Binds
    @Singleton
    fun bindRemoteDirectionService(service: RemoteDirectionServiceImpl): RemoteDirectionService
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

    @Provides
    @Singleton
    fun provideNsdManager(@ApplicationContext context: Context): NsdManager {
        return context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
}
