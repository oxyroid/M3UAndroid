@file:Suppress("unused")

package com.m3u.data.service

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.net.nsd.NsdManager
import androidx.core.app.NotificationManagerCompat
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.work.WorkManager
import com.m3u.core.architecture.FileProvider
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.settings
import com.m3u.data.service.internal.FileProviderImpl
import com.m3u.data.service.internal.MessagerImpl
import com.m3u.data.service.internal.PlayerManagerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface BindServicesModule {
    @Binds
    @Singleton
    fun bindPlayerManager(impl: PlayerManagerImpl): PlayerManager

    @Binds
    @Singleton
    fun bindMessageManager(service: MessagerImpl): Messager

    @Binds
    @Singleton
    fun bindFileProvider(provider: FileProviderImpl): FileProvider
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

    @Provides
    @Singleton
    fun provideDatabaseProvider(
        @ApplicationContext context: Context
    ): StandaloneDatabaseProvider = StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    fun provideSettings(
        @ApplicationContext context: Context
    ): Settings = context.settings

    @Provides
    @Singleton
    fun provideCache(
        @ApplicationContext applicationContext: Context,
        databaseProvider: StandaloneDatabaseProvider
    ): Cache {
        val downloadDirectory = File(
            applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir,
            "downloads"
        )
        return SimpleCache(
            downloadDirectory,
            LeastRecentlyUsedCacheEvictor(2L * 1024 * 1024 * 1024),
            databaseProvider
        )
    }

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext applicationContext: Context,
        databaseProvider: StandaloneDatabaseProvider,
        cache: Cache
    ): DownloadManager {
        return DownloadManager(
            applicationContext,
            databaseProvider,
            cache,
            DefaultDataSource.Factory(applicationContext),
            Executors.newFixedThreadPool(6)
        )
    }

    @Provides
    @Singleton
    fun provideAudioManager(
        @ApplicationContext applicationContext: Context
    ): AudioManager {
        return applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
}
