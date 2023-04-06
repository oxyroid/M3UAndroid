@file:Suppress("unused")

package com.m3u.data.remote.parser.di

import com.m3u.data.remote.parser.m3u.DefaultPlaylistParser
import com.m3u.data.remote.parser.m3u.PlaylistParser
import com.m3u.data.remote.parser.udp.UdpDiscover
import com.m3u.data.remote.parser.udp.UdpDiscoverImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface ParserModule {
    @Binds
    fun bindPlaylistParser(parser: DefaultPlaylistParser): PlaylistParser

    @Binds
    fun bindUDPDiscover(discoverImpl: UdpDiscoverImpl): UdpDiscover
}