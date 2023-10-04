@file:Suppress("unused")

package com.m3u.data.di

import com.m3u.core.architecture.Logger
import com.m3u.data.logger.CommonLogger
import com.m3u.data.logger.FileLogger
import com.m3u.data.logger.UiLogger
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
    fun bindCommonLogger(logger: CommonLogger): Logger

    @Binds
    @Singleton
    @Logger.File
    fun bindFileLogger(logger: FileLogger): Logger

    @Binds
    @Singleton
    @Logger.Ui
    fun bindUiLogger(logger: UiLogger): Logger
}