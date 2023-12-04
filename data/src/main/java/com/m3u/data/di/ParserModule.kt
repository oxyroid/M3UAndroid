@file:Suppress("unused")

package com.m3u.data.di

import com.m3u.data.parser.PlaylistParser
import com.m3u.data.parser.impl.BjoernPetersenPlaylistParser
import com.m3u.data.parser.impl.DefaultPlaylistParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface ParserModule {
    @Binds
    @PlaylistParser.Default
    fun bindPlaylistParser(parser: DefaultPlaylistParser): PlaylistParser

    @Binds
    @PlaylistParser.BjoernPetersen
    fun bindBjoernPetersenPlaylistParser(parser: BjoernPetersenPlaylistParser): PlaylistParser
}
