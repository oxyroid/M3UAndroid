@file:Suppress("unused")

package com.m3u.data.parser

import com.m3u.data.parser.epg.EpgParser
import com.m3u.data.parser.epg.EpgParserImpl
import com.m3u.data.parser.m3u.M3UParser
import com.m3u.data.parser.m3u.M3UParserImpl
import com.m3u.data.parser.xtream.XtreamParser
import com.m3u.data.parser.xtream.XtreamParserImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface ParserModule {
    @Binds
    fun bindM3UParser(parser: M3UParserImpl): M3UParser

    @Binds
    fun bindXtreamParser(parser: XtreamParserImpl): XtreamParser

    @Binds
    fun bindEpgParser(parser: EpgParserImpl): EpgParser
}
