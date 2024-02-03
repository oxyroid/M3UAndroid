@file:Suppress("unused")

package com.m3u.data.repository.parser

import com.m3u.data.repository.parser.internal.DefaultM3UPlaylistParser
import com.m3u.data.repository.parser.internal.VersionCatalogParserImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface ParserModule {
    @Binds
    @M3UPlaylistParser.Default
    fun bindM3UPlaylistParser(parser: DefaultM3UPlaylistParser): M3UPlaylistParser

    @Binds
    fun bindVersionCatalogParser(parser: VersionCatalogParserImpl): VersionCatalogParser
}
