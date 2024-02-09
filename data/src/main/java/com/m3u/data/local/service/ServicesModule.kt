@file:Suppress("unused")

package com.m3u.data.local.service

import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.wifi.WifiManager
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.m3u.core.architecture.TraceFileProvider
import com.m3u.core.architecture.logger.Logger
import com.m3u.data.local.http.HttpServer
import com.m3u.data.local.http.internal.HttpServerImpl
import com.m3u.data.local.nsd.NsdDeviceManager
import com.m3u.data.local.nsd.internal.NsdDeviceManagerImpl
import com.m3u.data.local.service.internal.MessageManagerImpl
import com.m3u.data.local.service.internal.PlayerManagerImpl
import com.m3u.data.local.service.internal.TraceFileProviderImpl
import com.m3u.data.repository.logger.CommonLogger
import com.m3u.data.repository.logger.MessageLogger
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
    fun bindMessageManager(service: MessageManagerImpl): MessageManager

    @Binds
    @Singleton
    fun bindTraceFileProvider(provider: TraceFileProviderImpl): TraceFileProvider

    @Binds
    @Singleton
    fun bindCommonLogger(logger: CommonLogger): Logger

    @Binds
    @Singleton
    @Logger.Message
    fun bindMessageLogger(logger: MessageLogger): Logger

    @Binds
    @Singleton
    fun bindNsdDeviceManager(manager: NsdDeviceManagerImpl): NsdDeviceManager

    @Binds
    @Singleton
    fun bindHttpServer(server: HttpServerImpl): HttpServer
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
    fun provideWifiManager(@ApplicationContext context: Context): WifiManager {
        return context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    @Provides
    @Singleton
    fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Provides
    @Singleton
    fun provideNsdManager(@ApplicationContext context: Context): NsdManager {
        return context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
}
