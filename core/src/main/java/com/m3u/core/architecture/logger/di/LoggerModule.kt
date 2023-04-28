@file:Suppress("unused")

package com.m3u.core.architecture.logger.di

import com.m3u.core.architecture.logger.AndroidLogger
import com.m3u.core.architecture.logger.BannerLogger
import com.m3u.core.architecture.logger.BannerLoggerImpl
import com.m3u.core.architecture.logger.FileLogger
import com.m3u.core.architecture.logger.FileLoggerImpl
import com.m3u.core.architecture.logger.Logger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface LoggerModule {
    @Binds
    @Singleton
    fun bindAndroidLogger(logger: AndroidLogger): Logger

    @Binds
    @Singleton
    @FileLoggerImpl
    fun bindFileLogger(logger: FileLogger): Logger

    @Binds
    @Singleton
    @BannerLoggerImpl
    fun bindBannerLogger(logger: BannerLogger): Logger
}