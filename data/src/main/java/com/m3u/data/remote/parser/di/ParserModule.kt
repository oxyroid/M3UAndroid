@file:Suppress("unused")

package com.m3u.data.remote.parser.di

import com.m3u.data.remote.parser.m3u.DefaultPlaylistParser
import com.m3u.data.remote.parser.m3u.PlaylistParser
import com.m3u.data.remote.parser.upnp.UdpParser
import com.m3u.data.remote.parser.upnp.UdpParserImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface ParserModule {
    @Binds
    fun bindPlaylistParser(parser: DefaultPlaylistParser): PlaylistParser

    @Binds
    @Singleton
    fun bindUdpParser(parser: UdpParserImpl): UdpParser
}