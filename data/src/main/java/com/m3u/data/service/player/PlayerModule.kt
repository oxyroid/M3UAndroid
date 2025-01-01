@file:Suppress("unused")

package com.m3u.data.service.player

import com.m3u.data.service.player.mediacommand.MediaCommandDecoder
import com.m3u.data.service.player.mediacommand.MediaCommandDecoderImpl
import com.m3u.data.service.player.useragent.UserAgentDecoder
import com.m3u.data.service.player.useragent.UserAgentDecoderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object PlayerProvidesModule

@Module
@InstallIn(SingletonComponent::class)
internal interface PlayerBindsModule {
    @Binds
    @Singleton
    fun bindPlayerManager(impl: PlayerManagerImpl): PlayerManager

    @Binds
    @Singleton
    fun bindMediaCommandDecoder(impl: MediaCommandDecoderImpl): MediaCommandDecoder

    @Binds
    @Singleton
    fun bindUserAgentDecoder(impl: UserAgentDecoderImpl): UserAgentDecoder
}