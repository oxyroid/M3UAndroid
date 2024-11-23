package com.m3u.data.runtime

import com.m3u.extension.api.tool.Logger
import com.m3u.extension.api.tool.Saver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface RuntimeBindModule {
    @Binds
    fun bindSaver(saver: SaverImpl): Saver
}

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeProvideModule {
    @Provides
    @Singleton
    fun provideLogger(): Logger = object : Logger {
        override fun log(text: String) {
            // TODO:
        }
    }

    @Provides
    fun provideJson(): Json = Json
}